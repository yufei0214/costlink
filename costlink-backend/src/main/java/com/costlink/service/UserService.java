package com.costlink.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.costlink.entity.AdminConfig;
import com.costlink.entity.User;
import com.costlink.exception.BusinessException;
import com.costlink.mapper.AdminConfigMapper;
import com.costlink.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final AdminConfigMapper adminConfigMapper;

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public User updateAlipayAccount(Long userId, String alipayAccount) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setAlipayAccount(alipayAccount);
        userMapper.updateById(user);
        return user;
    }

    private static final Set<String> VALID_DEPARTMENTS = Set.of(
        "研发1组-数据组",
        "研发1组-应用组",
        "研发1组-SDK组",
        "研发1组-质量组",
        "研发2组"
    );

    public User updateDepartment(Long userId, String department) {
        if (department == null || !VALID_DEPARTMENTS.contains(department)) {
            throw new BusinessException("所属组取值无效");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setDepartment(department);
        userMapper.updateById(user);
        return user;
    }

    public void updateAdminPayAccount(Long adminUserId, String alipayPayAccount) {
        AdminConfig config = adminConfigMapper.selectOne(
            new LambdaQueryWrapper<AdminConfig>().eq(AdminConfig::getAdminUserId, adminUserId)
        );

        if (config == null) {
            config = new AdminConfig();
            config.setAdminUserId(adminUserId);
            config.setAlipayPayAccount(alipayPayAccount);
            adminConfigMapper.insert(config);
        } else {
            config.setAlipayPayAccount(alipayPayAccount);
            adminConfigMapper.updateById(config);
        }
    }

    public AdminConfig getAdminConfig(Long adminUserId) {
        return adminConfigMapper.selectOne(
            new LambdaQueryWrapper<AdminConfig>().eq(AdminConfig::getAdminUserId, adminUserId)
        );
    }
}
