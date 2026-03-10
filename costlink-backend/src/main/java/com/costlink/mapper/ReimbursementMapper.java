package com.costlink.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.costlink.entity.Reimbursement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReimbursementMapper extends BaseMapper<Reimbursement> {

    @Select("SELECT r.*, u.username, u.display_name, u.alipay_account " +
            "FROM t_reimbursement r " +
            "LEFT JOIN t_user u ON r.user_id = u.id " +
            "WHERE r.user_id = #{userId} " +
            "ORDER BY r.created_at DESC")
    IPage<Reimbursement> selectByUserIdWithUser(Page<Reimbursement> page, @Param("userId") Long userId);

    @Select("SELECT r.*, u.username, u.display_name, u.alipay_account " +
            "FROM t_reimbursement r " +
            "LEFT JOIN t_user u ON r.user_id = u.id " +
            "ORDER BY r.created_at DESC")
    IPage<Reimbursement> selectAllWithUser(Page<Reimbursement> page);

    @Select("SELECT r.*, u.username, u.display_name, u.alipay_account " +
            "FROM t_reimbursement r " +
            "LEFT JOIN t_user u ON r.user_id = u.id " +
            "WHERE r.status = #{status} " +
            "ORDER BY r.created_at DESC")
    IPage<Reimbursement> selectByStatusWithUser(Page<Reimbursement> page, @Param("status") String status);

    @Select("SELECT r.*, u.username, u.display_name, u.alipay_account " +
            "FROM t_reimbursement r " +
            "LEFT JOIN t_user u ON r.user_id = u.id " +
            "WHERE r.id = #{id}")
    Reimbursement selectByIdWithUser(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM t_reimbursement WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
