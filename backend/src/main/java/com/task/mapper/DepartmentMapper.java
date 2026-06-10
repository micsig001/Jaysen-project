package com.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 根据企微部门 ID 查询部门
     */
    @Select("SELECT * FROM departments WHERE dept_id = #{deptId}")
    Department selectByDeptId(String deptId);

    /**
     * 批量根据 DeptID 查询部门
     */
    @Select("<script>" +
            "SELECT * FROM departments WHERE dept_id IN " +
            "<foreach collection='deptIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Department> selectByDeptIds(@Param("deptIds") List<String> deptIds);
}
