package com.base.admin.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/**
 * 用户导出 Excel 行 DTO
 */
@Data
@HeadRowHeight(22)
@ContentRowHeight(20)
public class UserExportRowDTO {

    @ExcelProperty("用户ID")
    @ColumnWidth(10)
    private Long userId;

    @ExcelProperty("用户名")
    @ColumnWidth(16)
    private String username;

    @ExcelProperty("昵称")
    @ColumnWidth(14)
    private String nickname;

    @ExcelProperty("邮箱")
    @ColumnWidth(24)
    private String email;

    @ExcelProperty("手机号")
    @ColumnWidth(16)
    private String phone;

    @ExcelProperty("性别")
    @ColumnWidth(10)
    private String gender;

    @ExcelProperty("账户状态")
    @ColumnWidth(12)
    private String status;

    @ExcelProperty("角色")
    @ColumnWidth(28)
    private String roles;

    @ExcelProperty("创建时间")
    @ColumnWidth(20)
    private String createTime;
}
