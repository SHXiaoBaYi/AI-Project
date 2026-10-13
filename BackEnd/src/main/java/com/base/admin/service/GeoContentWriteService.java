package com.base.admin.service;

import com.base.admin.domain.dto.GeoContentReviewDecideDTO;
import com.base.admin.domain.dto.GeoContentReviewQueryDTO;
import com.base.admin.domain.dto.GeoContentSubmitReviewDTO;
import com.base.admin.domain.dto.GeoContentWritingQueryDTO;
import com.base.admin.domain.vo.GeoContentReviewCompareVO;
import com.base.admin.domain.vo.GeoContentReviewRowVO;
import com.base.admin.domain.vo.GeoContentWritingRowVO;

import java.util.List;

/** 撰写人视角的在线撰写与多人审核 */
public interface GeoContentWriteService {

    List<GeoContentWritingRowVO> listMyWritings(GeoContentWritingQueryDTO query);

    List<GeoContentReviewRowVO> listPendingReviews();

    List<GeoContentReviewRowVO> listReviewRecords(GeoContentReviewQueryDTO query);

    /** 同一文章各轮审核记录 + 提审时正文快照（对照用） */
    GeoContentReviewCompareVO getReviewCompare(Long placementId);

    Long submitReview(GeoContentSubmitReviewDTO dto);

    void decide(GeoContentReviewDecideDTO dto);
}
