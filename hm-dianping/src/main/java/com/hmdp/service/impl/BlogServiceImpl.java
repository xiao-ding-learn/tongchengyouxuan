package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在！");
        }
        //查询用户信息
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLike(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        //根据用户查询
        Page<Blog> page = query()
                .orderByAsc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        //获取当页数据
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //判断是否点赞(根据isLike字段)
        String key=RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //未点赞，进行点赞数据库点赞数加一，存入redis中的set集合
        if(score == null){
            boolean isSuccess = update().setSql("liked  =liked + 1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else{
            //点赞，同理取消点赞
            boolean isSuccess = update().setSql("like=like-1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryLikes(Long id) {
        //查询点赞用户
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        //解析用户id
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5== null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //根据id查询用户
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //返回
        String idStr= StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("ids",ids).last("order by field(id,"+idStr+")").list()
                .stream()
                .map(user->BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //判空
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(tuple.getValue()));
            //获取时间戳
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
            ///根据id查询blog
            String idStr = StrUtil.join(",", ids);
            List<Blog> blogs = query().in("id", ids)
                    .last("order by field(id." + idStr + ")").list();

            for (Blog blog : blogs) {
                queryBlogUser(blog);
                isBlogLike(blog);
            }
            ScrollResult scrollResult = new ScrollResult();
            scrollResult.setList(blogs);
            scrollResult.setOffset(offset);
            scrollResult.setMinTime(minTime);


        return Result.ok(scrollResult);

    }

    private void isBlogLike(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = user.getId();
        //判断是否点赞(根据isLike字段)
        String key="blog:liked" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
