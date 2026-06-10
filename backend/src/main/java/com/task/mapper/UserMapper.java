package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.User;
import com.task.user.dto.RoleStatsVO;
import com.task.user.dto.UserNameVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据企微 UserID 查询用户
     */
    @Select("SELECT * FROM users WHERE user_id = #{userId}")
    User selectByUserId(String userId);

    /**
     * 批量根据 UserID 查询用户
     */
    @Select("<script>" +
            "SELECT * FROM users WHERE user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<User> selectByUserIds(@Param("userIds") List<String> userIds);

    /**
     * 根据部门 ID 查询本部门所有成员的 UserID 列表
     * 用途：归档模块经理权限过滤
     */
    @Select("SELECT user_id FROM users WHERE department_id = #{deptId} AND status = 1")
    List<String> selectUserIdsByDeptId(@Param("deptId") Long deptId);

    /**
     * 修复（P1-11）：批量按 UserID 查询 name（仅 user_id + name 两个字段）
     * 用途：可视化服务、其它只需要展示姓名的场景
     */
    @Select("<script>" +
            "SELECT user_id AS userId, name FROM users WHERE user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<UserNameVO> selectNamesByUserIds(@Param("userIds") List<String> userIds);

    /**
     * 修复（P1-12）：按角色分组统计启用账号人数（一次 GROUP BY 代替 3 次 count）
     */
    @Select("SELECT role, COUNT(*) AS count FROM users WHERE status = 1 GROUP BY role")
    List<RoleStatsVO> countByRoleGroup();
}
