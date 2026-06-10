package com.task.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户名+UserID 投影（用于可视化等只需要 id+name 的场景）
 *
 * <p>避免直接 selectList 把整行 users 表拖出来。</p>
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserNameVO {

    /** 企业微信 UserID（业务主键） */
    private String userId;

    /** 用户姓名 */
    private String name;
}
