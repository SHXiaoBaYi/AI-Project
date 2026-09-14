package com.base.admin.domain.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.ContentRowHeight;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

/**
 * 用户导入 Excel 行映射 DTO —— 模板列顺序不可随意调整。
 */
@Data
@HeadRowHeight(22)
@ContentRowHeight(20)
public class UserExcelRowDTO {

    @ExcelProperty(index = 0, value = "用户名（必填）")
    @ColumnWidth(22)
    private String username;

    @ExcelProperty(index = 1, value = "密码（必填）")
    @ColumnWidth(18)
    private String password;

    @ExcelProperty(index = 2, value = "昵称")
    @ColumnWidth(16)
    private String nickname;

    @ExcelProperty(index = 3, value = "邮箱")
    @ColumnWidth(26)
    private String email;

    @ExcelProperty(index = 4, value = "手机号")
    @ColumnWidth(18)
    private String phone;

    @ExcelProperty(index = 5, value = "性别")
    @ColumnWidth(14)
    private String gender;

    @ExcelProperty(index = 6, value = "账户状态")
    @ColumnWidth(14)
    private String status;
}
