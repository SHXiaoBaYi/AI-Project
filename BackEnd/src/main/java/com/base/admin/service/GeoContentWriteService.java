package com.base.admin.service;

import com.base.admin.domain.dto.GeoContentReviewAnnotationSaveDTO;
import com.base.admin.domain.dto.GeoContentReviewAnnotationUpdateDTO;
import com.base.admin.domain.dto.GeoContentReviewDecideDTO;
import com.base.admin.domain.dto.GeoContentReviewQueryDTO;
import com.base.admin.domain.dto.GeoContentSubmitReviewDTO;
import com.base.admin.domain.dto.GeoContentWritingQueryDTO;
import com.base.admin.domain.vo.GeoContentReviewAnnotationVO;
import com.base.admin.domain.vo.GeoContentReviewCompareVO;
import com.base.admin.domain.vo.GeoContentReviewRowVO;
import com.base.admin.domain.vo.GeoContentWritingRowVO;

import java.util.List;

/** 撰写人视角的在线撰写与两阶段串行审核 */
public interface GeoContentWriteService {

    List<GeoContentWritingRowVO> listMyWritings(GeoContentWritingQueryDTO query);

    List<GeoContentReviewRowVO> listPendingReviews();

    List<GeoContentReviewRowVO> listReviewRecords(GeoContentReviewQueryDTO query);

    /** 同一文章各轮审核记录 + 提审时正文快照（对照用） */
    GeoContentReviewCompareVO getReviewCompare(Long placementId);

    Long submitReview(GeoContentSubmitReviewDTO dto);

    void decide(GeoContentReviewDecideDTO dto);

    /** 审核人划词/划句/划段批注（本轮审核进行中） */
    GeoContentReviewAnnotationVO addAnnotation(GeoContentReviewAnnotationSaveDTO dto);

    /** 修改本人本轮划词批注内容 */
    GeoContentReviewAnnotationVO updateAnnotation(GeoContentReviewAnnotationUpdateDTO dto);

    /** 删除本人本轮未结束审核中的划词批注 */
    void deleteAnnotation(Long annotationId);
}
