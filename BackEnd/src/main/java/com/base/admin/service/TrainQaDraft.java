package com.base.admin.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 从培训原文件拆出的一道问答，尚未入库。 */
@Data
public class TrainQaDraft {

    private int sortNo;
    private long docId;
    private long versionId;
    private String versionLabel;
    private String question;
    private String answer;
    private String answerHtml;
    private List<String> images = new ArrayList<>();
}
