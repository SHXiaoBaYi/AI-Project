package com.base.admin.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "本机采集总览")
public class GeoWorkerBoardVO {

    @Schema(description = "已报过心跳的采集电脑")
    private List<GeoWorkerNodeVO> nodes = new ArrayList<>();

    @Schema(description = "等待领取的题数")
    private int pendingJobs;

    @Schema(description = "已被本机领走、尚未回传的题数")
    private int leasedJobs;
}
