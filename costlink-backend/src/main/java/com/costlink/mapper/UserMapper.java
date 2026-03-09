package com.costlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.costlink.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
