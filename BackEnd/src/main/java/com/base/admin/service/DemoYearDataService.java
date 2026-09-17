package com.base.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.admin.common.Constants;
import com.base.admin.domain.dto.GeoBoardQueryDTO;
import com.base.admin.domain.entity.GeoContentPlacement;
import com.base.admin.domain.entity.GeoContentPlacementCite;
import com.base.admin.domain.entity.GeoContentPlacementItem;
import com.base.admin.domain.entity.GeoMonitorDaily;
import com.base.admin.domain.entity.GeoTopic;
import com.base.admin.domain.entity.GeoYearTarget;
import com.base.admin.domain.entity.SysTask;
import com.base.admin.domain.entity.SysTaskAssignee;
import com.base.admin.domain.entity.SysUser;
import com.base.admin.mapper.GeoContentPlacementCiteMapper;
import com.base.admin.mapper.GeoContentPlacementItemMapper;
import com.base.admin.mapper.GeoContentPlacementMapper;
import com.base.admin.mapper.GeoMonitorDailyMapper;
import com.base.admin.mapper.GeoTopicMapper;
import com.base.admin.mapper.GeoYearTargetMapper;
import com.base.admin.mapper.SysTaskAssigneeMapper;
import com.base.admin.mapper.SysTaskFileMapper;
import com.base.admin.mapper.SysTaskMapper;
import com.base.admin.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 刷入 2014 / 2015 全年演示数据（GEO 监测、内容投放、任务），全部打 is_demo=1。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoYearDataService {

    private static final int QUESTIONS_PER_YEAR = 50;
    /** 每年日监测目标规模约 1 万：365 天 × 3 平台 × 9 关键词 ≈ 9855 */
    private static final int DAILY_KEYWORDS_PER_PLATFORM = 9;
    private static final int[] YEARS = {2014, 2015};

    private static final String[] DEMO_TOPICS = {
            "演示-新疆特产", "演示-送礼场景", "演示-文旅出行", "演示-美食推荐", "演示-健康养生"
    };

    private static final String[] AI_PLATFORMS = {"豆包", "DS", "元宝"};
    private static final String[] CONTENT_PLATFORMS = {"搜狐", "今日头条", "小红书", "百家号"};
    /** 各话题露出率基线（再叠加平台/日噪声），避免全局 0/50/100 */
    private static final double[] TOPIC_MENTION_BASE = {0.58, 0.71, 0.64, 0.79, 0.52};
    private static final String[] AGG_STATUSES = {
            Constants.CONTENT_AGG_NONE, Constants.CONTENT_AGG_PARTIAL, Constants.CONTENT_AGG_DONE
    };
    private static final String[] PUBLISH_STATUSES = {
            Constants.CONTENT_PUBLISH_NONE, Constants.CONTENT_PUBLISH_REJECTED, Constants.CONTENT_PUBLISH_SUCCESS
    };
    private static final String[] SOURCES = {
            Constants.CONTENT_SOURCE_IMPORT, Constants.CONTENT_SOURCE_MANUAL, Constants.CONTENT_SOURCE_AI
    };
    private static final String[] TASK_STATUSES = {"待分配", "未开始", "进行中", "已完成", "已取消"};
    private static final String[] TASK_TYPES = {
            Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER,
            Constants.TASK_TYPE_GEO_ASSIGN_WRITER,
            "文章撰写",
            "文章发布",
            "日常"
    };

    private final GeoTopicMapper topicMapper;
    private final GeoMonitorDailyMapper dailyMapper;
    private final GeoYearTargetMapper targetMapper;
    private final GeoContentPlacementMapper placementMapper;
    private final GeoContentPlacementItemMapper itemMapper;
    private final GeoContentPlacementCiteMapper citeMapper;
    private final SysTaskMapper taskMapper;
    private final SysTaskAssigneeMapper assigneeMapper;
    private final SysTaskFileMapper taskFileMapper;
    private final SysUserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;
    private final GeoPlatformService platformService;
    private final GeoMonitorService monitorService;
    private final GeoContentPlacementService contentPlacementService;

    public boolean hasDemoData() {
        return placementMapper.selectCount(new LambdaQueryWrapper<GeoContentPlacement>()
                .eq(GeoContentPlacement::getIsDemo, 1)) > 0;
    }

    /** 仅按现有演示日监测重建 2014/2015 周月年快照（不重刷日表） */
    public String rebuildBoardsOnly() {
        if (!hasDemoData()) {
            return "无演示数据，请先 seed";
        }
        alignDemoCreateTimes();
        int reshuffled = reshuffleDemoDailyRates();
        persistBoards();
        return "已重洗露出率 " + reshuffled + " 条，并重建 2014/2015 周/月/年快照";
    }

    /** 把演示业务行的 create_time 铺到 2014-2015，便于列表「创建时间」筛选命中 */
    private void alignDemoCreateTimes() {
        int daily = jdbcTemplate.update("""
                UPDATE geo_monitor_daily
                SET create_time = TIMESTAMP(inspect_date, '10:00:00'),
                    update_time = TIMESTAMP(inspect_date, '10:00:00')
                WHERE is_demo = 1
                """);
        int placement = jdbcTemplate.update("""
                UPDATE geo_content_placement
                SET create_time = DATE_ADD('2014-01-01 09:00:00', INTERVAL (id % 730) DAY),
                    update_time = DATE_ADD('2014-01-01 09:00:00', INTERVAL (id % 730) DAY)
                WHERE is_demo = 1
                """);
        jdbcTemplate.update("""
                UPDATE geo_content_placement_item i
                INNER JOIN geo_content_placement p ON i.placement_id = p.id
                SET i.create_time = COALESCE(TIMESTAMP(i.publish_time, '11:00:00'), p.create_time),
                    i.update_time = COALESCE(TIMESTAMP(i.publish_time, '11:00:00'), p.create_time)
                WHERE p.is_demo = 1
                """);
        jdbcTemplate.update("""
                UPDATE geo_content_placement_cite c
                INNER JOIN geo_content_placement p ON c.placement_id = p.id
                SET c.create_time = p.create_time, c.update_time = p.create_time
                WHERE p.is_demo = 1
                """);
        int task = jdbcTemplate.update("""
                UPDATE sys_task
                SET create_time = COALESCE(plan_start_time, DATE_ADD('2014-01-01 09:00:00', INTERVAL (id % 730) DAY)),
                    update_time = COALESCE(plan_end_time, DATE_ADD('2014-01-01 09:00:00', INTERVAL (id % 730) DAY))
                WHERE is_demo = 1
                """);
        int topic = jdbcTemplate.update("""
                UPDATE geo_topic
                SET create_time = '2014-01-01 08:00:00', update_time = '2014-01-01 08:00:00'
                WHERE is_demo = 1
                """);
        int target = jdbcTemplate.update("""
                UPDATE geo_year_target
                SET create_time = COALESCE(TIMESTAMP(period_start, '08:00:00'), '2014-01-01 08:00:00'),
                    update_time = COALESCE(TIMESTAMP(period_end, '08:00:00'), '2015-12-31 08:00:00')
                WHERE is_demo = 1
                """);
        log.info("演示 create_time 回填: daily={}, placement={}, task={}, topic={}, target={}",
                daily, placement, task, topic, target);
    }

    /** 不包超大事务：约 2 万条日监测按批提交，避免长事务锁表/超时 */
    public String seed(boolean force) {
        if (!force && hasDemoData()) {
            return "演示数据已存在，跳过（可设置 demo.reseed=true 强制重刷）";
        }

        ensurePlatforms();
        List<SysUser> users = loadActiveUsers();
        if (users.isEmpty()) {
            return "无可用用户，无法刷演示数据";
        }

        if (force) {
            clearDemoData();
        }

        List<GeoTopic> topics = seedTopics();
        seedYearTargets(topics);

        int placementCount = 0;
        int itemCount = 0;
        int citeCount = 0;
        int taskCount = 0;
        int dailyCount = 0;

        for (int year : YEARS) {
            List<String> questions = buildQuestions(year);
            for (int i = 0; i < questions.size(); i++) {
                SeedCounts c = seedPlacementBundle(year, i, questions.get(i), topics.get(i % topics.size()), users);
                placementCount += c.placements;
                itemCount += c.items;
                citeCount += c.cites;
                taskCount += c.tasks;
            }
            dailyCount += seedDailyForYear(year, questions, topics, users);
            log.info("演示数据进度: {} 年投放/任务完成，日监测累计 {}", year, dailyCount);
        }

        alignDemoCreateTimes();
        persistBoards();

        return String.format(
                "演示数据刷入完成：话题%d / 投放%d / 明细%d / 引用%d / 任务%d / 日监测%d（约1万条/年·每天有数，is_demo=1）",
                topics.size(), placementCount, itemCount, citeCount, taskCount, dailyCount);
    }

    private void ensurePlatforms() {
        for (String p : AI_PLATFORMS) {
            platformService.getOrCreate(p, Constants.PLATFORM_TYPE_AI);
        }
        for (String p : CONTENT_PLATFORMS) {
            platformService.getOrCreate(p, Constants.PLATFORM_TYPE_CONTENT);
        }
    }

    private List<SysUser> loadActiveUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsActive, 1)
                .eq(SysUser::getStatus, 0)
                .orderByAsc(SysUser::getUserId)
                .last("LIMIT 20"));
    }

    private List<GeoTopic> seedTopics() {
        List<GeoTopic> list = new ArrayList<>();
        for (int i = 0; i < DEMO_TOPICS.length; i++) {
            String name = DEMO_TOPICS[i];
            GeoTopic existing = topicMapper.selectOne(new LambdaQueryWrapper<GeoTopic>()
                    .eq(GeoTopic::getTopicName, name)
                    .eq(GeoTopic::getIsDemo, 1)
                    .last("LIMIT 1"));
            if (existing != null) {
                list.add(existing);
                continue;
            }
            GeoTopic t = new GeoTopic();
            t.setTopicName(name);
            t.setOptimizeWeek("演示全年");
            t.setRemark(Constants.DEMO_MARKER + "2014-2015");
            t.setIsDemo(1);
            t.setIsActive(1);
            t.setCreateBy(Constants.DEMO_CREATE_BY);
            t.setUpdateBy(Constants.DEMO_CREATE_BY);
            topicMapper.insert(t);
            list.add(t);
        }
        return list;
    }

    private void seedYearTargets(List<GeoTopic> topics) {
        String[] periodLabels = {"全年", "上半年", "下半年", "Q1", "Q2", "Q3", "Q4"};
        for (int year : YEARS) {
            LocalDate[][] ranges = {
                    {LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)},
                    {LocalDate.of(year, 1, 1), LocalDate.of(year, 6, 30)},
                    {LocalDate.of(year, 7, 1), LocalDate.of(year, 12, 31)},
                    {LocalDate.of(year, 1, 1), LocalDate.of(year, 3, 31)},
                    {LocalDate.of(year, 4, 1), LocalDate.of(year, 6, 30)},
                    {LocalDate.of(year, 7, 1), LocalDate.of(year, 9, 30)},
                    {LocalDate.of(year, 10, 1), LocalDate.of(year, 12, 31)},
            };
            for (GeoTopic topic : topics) {
                for (int p = 0; p < periodLabels.length; p++) {
                    GeoYearTarget t = new GeoYearTarget();
                    t.setPeriodLabel(year + periodLabels[p]);
                    t.setPeriodStart(ranges[p][0]);
                    t.setPeriodEnd(ranges[p][1]);
                    t.setTopicId(topic.getId());
                    t.setTargetRate(BigDecimal.valueOf(70 + (p * 3) % 25));
                    t.setSortOrder(year * 100 + p);
                    t.setRemark(Constants.DEMO_MARKER);
                    t.setIsDemo(1);
                    t.setCreateBy(Constants.DEMO_CREATE_BY);
                    t.setUpdateBy(Constants.DEMO_CREATE_BY);
                    targetMapper.insert(t);
                }
            }
        }
    }

    private List<String> buildQuestions(int year) {
        String[] stems = {
                "新疆适合寄内地的礼品有哪些",
                "冬天去新疆旅游买什么特产",
                "新疆羊肉哪里买靠谱",
                "新疆葡萄干怎么选",
                "送给长辈的新疆伴手礼推荐",
                "新疆大枣和和田枣有什么区别",
                "适合办公室分享的新疆零食",
                "新疆文旅必买清单",
                "南疆北疆特产对比",
                "新疆蜂蜜真假怎么辨别"
        };
        List<String> list = new ArrayList<>(QUESTIONS_PER_YEAR);
        for (int i = 1; i <= QUESTIONS_PER_YEAR; i++) {
            String stem = stems[(i - 1) % stems.length];
            list.add(Constants.DEMO_MARKER + year + "年第" + i + "题：" + stem);
        }
        return list;
    }

    private SeedCounts seedPlacementBundle(int year, int index, String question, GeoTopic topic, List<SysUser> users) {
        SysUser publisher = users.get(index % users.size());
        SysUser writer = users.get((index + 1) % users.size());
        String agg = AGG_STATUSES[index % AGG_STATUSES.length];
        String source = SOURCES[index % SOURCES.length];
        boolean unassignedPublisher = index % 7 == 0;
        boolean unassignedWriter = index % 11 == 0;

        LocalDate baseDate = LocalDate.of(year, 1 + (index % 12), 1 + (index % 25));

        GeoContentPlacement placement = new GeoContentPlacement();
        placement.setTargetQuestion(question);
        placement.setTitle(Constants.DEMO_MARKER + year + "内容标题-" + (index + 1));
        placement.setTopicId(topic.getId());
        placement.setTopicName(topic.getTopicName());
        placement.setSource(source);
        placement.setPlacementProgress(agg);
        if (unassignedPublisher) {
            placement.setPublisherName(Constants.CONTENT_UNASSIGNED);
            placement.setPublisherUserId(null);
        } else {
            placement.setPublisherUserId(publisher.getUserId());
            placement.setPublisherName(displayName(publisher));
        }
        if (unassignedWriter) {
            placement.setOwnerName(Constants.CONTENT_UNASSIGNED);
            placement.setOwnerUserId(null);
        } else {
            placement.setOwnerUserId(writer.getUserId());
            placement.setOwnerName(displayName(writer));
        }
        placement.setRemark(Constants.DEMO_MARKER + year + "投放");
        placement.setIsDemo(1);
        placement.setCreateBy(Constants.DEMO_CREATE_BY);
        placement.setUpdateBy(Constants.DEMO_CREATE_BY);
        placementMapper.insert(placement);

        int items = 0;
        int cites = 0;
        Long firstItemId = null;
        // 每个投放覆盖全部发布状态（循环取平台）
        for (int s = 0; s < PUBLISH_STATUSES.length; s++) {
            String platform = CONTENT_PLATFORMS[(index + s) % CONTENT_PLATFORMS.length];
            String status = PUBLISH_STATUSES[s];
            // 按聚合进度约束；投放完成则三平台均成功，发布时间铺满全年各周
            if (Constants.CONTENT_AGG_NONE.equals(agg)) {
                status = Constants.CONTENT_PUBLISH_NONE;
            } else if (Constants.CONTENT_AGG_DONE.equals(agg)) {
                status = Constants.CONTENT_PUBLISH_SUCCESS;
            } else if (Constants.CONTENT_AGG_PARTIAL.equals(agg) && s == 0) {
                status = Constants.CONTENT_PUBLISH_SUCCESS;
            }

            GeoContentPlacementItem item = new GeoContentPlacementItem();
            item.setPlacementId(placement.getId());
            item.setTitle(placement.getTitle() + "-" + platform);
            item.setPlatformName(platform);
            item.setContentForm(s % 2 == 0 ? Constants.CONTENT_FORM_ARTICLE : Constants.CONTENT_FORM_VIDEO);
            item.setPublishStatus(status);
            if (Constants.CONTENT_PUBLISH_SUCCESS.equals(status)) {
                item.setPublishUrl("https://demo.example.com/" + year + "/" + placement.getId() + "/" + s);
                // 按题号铺到全年不同周，避免内容周/月报扎堆或空窗
                int dayOffset = Math.min(364, (index * 7 + s * 3) % 365);
                item.setPublishTime(LocalDate.of(year, 1, 1).plusDays(dayOffset));
            }
            item.setSortOrder(s);
            item.setRemark(Constants.DEMO_MARKER);
            item.setIsDemo(1);
            item.setCreateBy(Constants.DEMO_CREATE_BY);
            item.setUpdateBy(Constants.DEMO_CREATE_BY);
            itemMapper.insert(item);
            items++;
            if (firstItemId == null) {
                firstItemId = item.getId();
            }
        }

        if (index % 2 == 0) {
            GeoContentPlacementCite cite = new GeoContentPlacementCite();
            cite.setPlacementId(placement.getId());
            cite.setItemId(firstItemId);
            cite.setAskQuestion(question);
            cite.setAiPlatform(AI_PLATFORMS[index % AI_PLATFORMS.length]);
            cite.setCiteUrl("https://demo.example.com/cite/" + placement.getId());
            cite.setSortOrder(0);
            cite.setRemark(Constants.DEMO_MARKER);
            cite.setIsDemo(1);
            cite.setCreateBy(Constants.DEMO_CREATE_BY);
            cite.setUpdateBy(Constants.DEMO_CREATE_BY);
            citeMapper.insert(cite);
            cites++;
        }

        int tasks = seedTasksForPlacement(placement, year, index, users, unassignedPublisher, unassignedWriter, baseDate);
        return new SeedCounts(1, items, cites, tasks);
    }

    private int seedTasksForPlacement(GeoContentPlacement placement, int year, int index,
                                      List<SysUser> users, boolean unassignedPublisher,
                                      boolean unassignedWriter, LocalDate baseDate) {
        int created = 0;
        // 覆盖全部任务状态：按 index 轮转主状态，并额外造一条对照状态
        String primaryStatus = TASK_STATUSES[index % TASK_STATUSES.length];
        created += insertTask(placement, TASK_TYPES[index % TASK_TYPES.length], primaryStatus,
                users.get(index % users.size()), users.get((index + 2) % users.size()),
                baseDate, year, index, null);

        // 待分配发布人/撰写人缺口
        if (unassignedPublisher) {
            created += insertTask(placement, Constants.TASK_TYPE_GEO_ASSIGN_PUBLISHER, "待分配",
                    users.get(0), null, baseDate, year, index, null);
        }
        if (unassignedWriter) {
            created += insertTask(placement, Constants.TASK_TYPE_GEO_ASSIGN_WRITER, "待分配",
                    users.get(0), null, baseDate.plusDays(1), year, index, null);
        }

        // 保证本批次至少出现过所有状态（前 5 条强制各造一种）
        if (index < TASK_STATUSES.length) {
            String forceStatus = TASK_STATUSES[index];
            if (!forceStatus.equals(primaryStatus)) {
                created += insertTask(placement, "日常", forceStatus,
                        users.get(index % users.size()), users.get((index + 1) % users.size()),
                        baseDate.plusDays(2), year, index, null);
            }
        }
        return created;
    }

    private int insertTask(GeoContentPlacement placement, String taskType, String status,
                           SysUser creator, SysUser ownerOrNull, LocalDate baseDate,
                           int year, int index, Long parentId) {
        SysUser owner = ownerOrNull != null ? ownerOrNull : creator;
        boolean pending = "待分配".equals(status);

        SysTask task = new SysTask();
        task.setTitle(Constants.DEMO_MARKER + year + "-" + taskType + "-" + (index + 1));
        task.setContent("演示任务，覆盖状态「" + status + "」");
        task.setTaskType(taskType);
        task.setPriority(1 + (index % 4));
        task.setStatus(status);
        task.setProgress("已完成".equals(status) ? 100 : ("进行中".equals(status) ? 40 + (index % 50) : 0));
        task.setCreatorUserId(creator.getUserId());
        task.setCreatorName(displayName(creator));
        task.setOwnerUserId(owner.getUserId());
        task.setOwnerName(displayName(owner));
        task.setPlanStartTime(baseDate.atTime(9, 0));
        task.setPlanEndTime(baseDate.plusDays(7).atTime(18, 0));
        if ("进行中".equals(status) || "已完成".equals(status)) {
            task.setActualStartTime(baseDate.plusDays(1).atTime(10, 0));
        }
        if ("已完成".equals(status)) {
            task.setActualEndTime(baseDate.plusDays(5).atTime(17, 0));
        }
        task.setBizType(Constants.TASK_BIZ_GEO_CONTENT_PLACEMENT);
        task.setBizId(placement.getId());
        task.setBizTitle(placement.getTitle());
        task.setParentId(parentId);
        task.setSortOrder(index);
        task.setRemark(Constants.DEMO_MARKER);
        task.setIsDemo(1);
        task.setCreateBy(Constants.DEMO_CREATE_BY);
        task.setUpdateBy(Constants.DEMO_CREATE_BY);
        taskMapper.insert(task);

        if (!pending) {
            SysTaskAssignee a = new SysTaskAssignee();
            a.setTaskId(task.getId());
            a.setUserId(owner.getUserId());
            a.setUserName(displayName(owner));
            a.setRoleLabel("执行");
            a.setDone("已完成".equals(status) ? 1 : 0);
            a.setCreateBy(Constants.DEMO_CREATE_BY);
            a.setUpdateBy(Constants.DEMO_CREATE_BY);
            assigneeMapper.insert(a);
        }
        return 1;
    }

    private int seedDailyForYear(int year, List<String> questions, List<GeoTopic> topics, List<SysUser> users) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        int questionCount = questions.size();
        String sql = """
                INSERT INTO geo_monitor_daily
                (inspect_date, term_type, platform, keyword, owner_user_id, owner_name, topic_id,
                 mentioned, rank_no, recommend_status, negative_content, competitors, board_locked,
                 is_demo, create_by, create_time, update_by, update_time, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, NOW(), ?, NOW(), 1)
                """;
        List<Object[]> batch = new ArrayList<>(500);
        int count = 0;
        // 每天全量：3 平台 × 9 关键词 ≈ 9855 条/年；露出率按话题基线 + 噪声随机，避免 0/50/100 台阶
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            int dayOfYear = day.getDayOfYear();
            boolean monday = day.getDayOfWeek() == DayOfWeek.MONDAY;
            for (int pi = 0; pi < AI_PLATFORMS.length; pi++) {
                String platform = AI_PLATFORMS[pi];
                for (int k = 0; k < DAILY_KEYWORDS_PER_PLATFORM; k++) {
                    int qi = (dayOfYear - 1 + k * 7 + pi) % questionCount;
                    String keyword = questions.get(qi);
                    GeoTopic topic = topics.get(qi % topics.size());
                    SysUser owner = users.get((dayOfYear + k + pi) % users.size());
                    DemoMentionRoll roll = rollMention(year, dayOfYear, pi, k, qi % topics.size());
                    String negative = roll.mentioned && ThreadLocalRandom.current().nextInt(100) < 8
                            ? Constants.DEMO_MARKER + "负面样例" : null;
                    String competitors = roll.mentioned && ThreadLocalRandom.current().nextInt(100) < 18
                            ? "竞品A,竞品B" : null;
                    String termType = monday && k == 0 ? Constants.TERM_TYPE_WEEKLY : Constants.TERM_TYPE_DAILY;
                    batch.add(new Object[]{
                            java.sql.Date.valueOf(day),
                            termType,
                            platform,
                            keyword,
                            owner.getUserId(),
                            displayName(owner),
                            topic.getId(),
                            roll.mentioned ? 1 : 0,
                            roll.rankNo,
                            roll.recommendStatus,
                            negative,
                            competitors,
                            Constants.DEMO_CREATE_BY,
                            Constants.DEMO_CREATE_BY
                    });
                    count++;
                    if (batch.size() >= 500) {
                        jdbcTemplate.batchUpdate(sql, batch);
                        batch.clear();
                    }
                }
            }
            if (dayOfYear % 60 == 0) {
                log.info("演示日监测进度: {} 已写入 {} 条", day, count);
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        return count;
    }

    /**
     * 按话题基线 + 平台偏置 + 日噪声抽露出结果，聚合后露出率会落在较连续的区间，而不是 0/50/100。
     */
    private static DemoMentionRoll rollMention(int year, int dayOfYear, int platformIdx, int keywordIdx, int topicIdx) {
        long seed = (((long) year * 1000L + dayOfYear) * 31 + platformIdx) * 17 + keywordIdx * 13L + topicIdx;
        java.util.Random rng = new java.util.Random(seed);
        double base = TOPIC_MENTION_BASE[Math.floorMod(topicIdx, TOPIC_MENTION_BASE.length)];
        double platformBias = (platformIdx - 1) * 0.04; // -0.04 / 0 / +0.04
        double noise = (rng.nextGaussian()) * 0.12;
        double pMention = Math.min(0.92, Math.max(0.18, base + platformBias + noise));
        boolean mentioned = rng.nextDouble() < pMention;
        if (!mentioned) {
            return new DemoMentionRoll(false, null, "未出现");
        }
        // 已露出中：约 35%~70% 带推荐（随随机），排名 1~8 偏向前排
        double pRecommend = 0.35 + rng.nextDouble() * 0.35;
        boolean recommend = rng.nextDouble() < pRecommend;
        int rank = 1 + (int) Math.floor(Math.pow(rng.nextDouble(), 1.6) * 8); // 偏向前排
        if (rank > 8) {
            rank = 8;
        }
        return new DemoMentionRoll(true, rank, recommend ? "出现且推荐" : "出现未推荐");
    }

    private record DemoMentionRoll(boolean mentioned, Integer rankNo, String recommendStatus) {}

    /** 就地重洗已有演示日监测的露出/推荐/排名（无需整表重造） */
    public int reshuffleDemoDailyRates() {
        // 取 topic 顺序用于基线：按话题 id 映射到 0..n
        List<Long> topicIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT topic_id FROM geo_monitor_daily WHERE is_demo = 1 ORDER BY topic_id", Long.class);
        java.util.Map<Long, Integer> topicIndex = new java.util.HashMap<>();
        for (int i = 0; i < topicIds.size(); i++) {
            topicIndex.put(topicIds.get(i), i);
        }
        String sql = """
                UPDATE geo_monitor_daily
                SET mentioned = ?, rank_no = ?, recommend_status = ?,
                    negative_content = ?, competitors = ?, update_by = ?, update_time = NOW()
                WHERE id = ? AND is_demo = 1
                """;
        List<Object[]> batch = new ArrayList<>(500);
        int updated = 0;
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, inspect_date, platform, topic_id, keyword
                FROM geo_monitor_daily WHERE is_demo = 1 AND is_active = 1
                """);
        for (java.util.Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            LocalDate day = ((java.sql.Date) row.get("inspect_date")).toLocalDate();
            String platform = String.valueOf(row.get("platform"));
            Long topicId = row.get("topic_id") == null ? null : ((Number) row.get("topic_id")).longValue();
            int topicIdx = topicIndex.getOrDefault(topicId, 0);
            int pi = 0;
            for (int i = 0; i < AI_PLATFORMS.length; i++) {
                if (AI_PLATFORMS[i].equals(platform)) {
                    pi = i;
                    break;
                }
            }
            int k = (int) (id % DAILY_KEYWORDS_PER_PLATFORM);
            DemoMentionRoll roll = rollMention(day.getYear(), day.getDayOfYear(), pi, k, topicIdx);
            String negative = roll.mentioned && ThreadLocalRandom.current().nextInt(100) < 8
                    ? Constants.DEMO_MARKER + "负面样例" : null;
            String competitors = roll.mentioned && ThreadLocalRandom.current().nextInt(100) < 18
                    ? "竞品A,竞品B" : null;
            batch.add(new Object[]{
                    roll.mentioned ? 1 : 0,
                    roll.rankNo,
                    roll.recommendStatus,
                    negative,
                    competitors,
                    Constants.DEMO_CREATE_BY,
                    id
            });
            updated++;
            if (batch.size() >= 500) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        log.info("演示日监测露出率已重洗 {} 条", updated);
        return updated;
    }

    private void persistBoards() {
        // 先清 2014-2015 旧快照并解锁演示日数据，再按当前日监测重算，避免快照与日表脱节
        int clearedBoard = jdbcTemplate.update("""
                DELETE FROM geo_board_period_stat
                WHERE period_end >= '2014-01-01' AND period_start <= '2015-12-31'
                """);
        int clearedContent = jdbcTemplate.update("""
                DELETE FROM geo_content_period_stat
                WHERE period_end >= '2014-01-01' AND period_start <= '2015-12-31'
                """);
        int unlocked = jdbcTemplate.update("UPDATE geo_monitor_daily SET board_locked = 0 WHERE is_demo = 1");
        log.info("演示看板重建前置：清除监测快照 {}、内容快照 {}，解锁日监测 {}",
                clearedBoard, clearedContent, unlocked);

        GeoBoardQueryDTO q2014 = new GeoBoardQueryDTO();
        q2014.setStartDate(LocalDate.of(2014, 1, 1));
        q2014.setEndDate(LocalDate.of(2014, 12, 31));
        GeoBoardQueryDTO q2015 = new GeoBoardQueryDTO();
        q2015.setStartDate(LocalDate.of(2015, 1, 1));
        q2015.setEndDate(LocalDate.of(2015, 12, 31));
        try {
            var w14 = monitorService.persistWeeklyBoard(q2014);
            var m14 = monitorService.persistMonthlyBoard(q2014);
            var y14 = monitorService.persistYearlyBoard(q2014);
            var w15 = monitorService.persistWeeklyBoard(q2015);
            var m15 = monitorService.persistMonthlyBoard(q2015);
            var y15 = monitorService.persistYearlyBoard(q2015);
            log.info("演示监测看板落库: 2014周{}月{}年{} | 2015周{}月{}年{}",
                    w14.getSnapshotCount(), m14.getSnapshotCount(), y14.getSnapshotCount(),
                    w15.getSnapshotCount(), m15.getSnapshotCount(), y15.getSnapshotCount());
            assertBoardAlignedWithDaily(2014);
            assertBoardAlignedWithDaily(2015);
        } catch (Exception e) {
            log.error("演示数据看板落库（监测）失败", e);
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
        try {
            var content = contentPlacementService.persistContentBoardRange(
                    LocalDate.of(2014, 1, 1), LocalDate.of(2015, 12, 31));
            log.info("演示内容看板落库: period={}, snapshot={}",
                    content.getPeriodCount(), content.getSnapshotCount());
        } catch (Exception e) {
            log.warn("演示数据看板落库（内容）部分失败: {}", e.getMessage());
        }
    }

    /**
     * 抽检：周/月快照 sample_count 之和应等于对应日监测条数（按话题+平台拆分后按权重对齐）。
     * 这里用「全年日监测总数」与「月快照 sample 合计」对比，偏差超过 1% 则失败。
     */
    private void assertBoardAlignedWithDaily(int year) {
        Integer dailyTotal = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM geo_monitor_daily
                WHERE is_demo = 1 AND is_active = 1
                  AND inspect_date BETWEEN ? AND ?
                """, Integer.class, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        Integer monthSamples = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(sample_count), 0) FROM geo_board_period_stat
                WHERE period_type = 'MONTH'
                  AND period_start >= ? AND period_end <= ?
                  AND topic_id IN (SELECT id FROM geo_topic WHERE is_demo = 1)
                """, Integer.class, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        int daily = dailyTotal == null ? 0 : dailyTotal;
        int month = monthSamples == null ? 0 : monthSamples;
        if (daily <= 0) {
            throw new IllegalStateException(year + " 演示日监测为空，无法校验看板");
        }
        double ratio = Math.abs(month - daily) * 1.0 / daily;
        if (ratio > 0.01) {
            throw new IllegalStateException(String.format(
                    "%d 年月报快照 sample=%d 与日监测=%d 偏差 %.2f%%，请检查落库口径",
                    year, month, daily, ratio * 100));
        }
        log.info("演示看板对齐校验通过: {} 日监测={} 月快照sample合计={}", year, daily, month);
    }

    public void clearDemoData() {
        // 物理删除，避免逻辑删仍占用 uk_date_platform_keyword_term
        jdbcTemplate.update("""
                DELETE a FROM sys_task_assignee a
                INNER JOIN sys_task t ON a.task_id = t.id
                WHERE t.is_demo = 1
                """);
        jdbcTemplate.update("""
                DELETE f FROM sys_task_file f
                INNER JOIN sys_task t ON f.task_id = t.id
                WHERE t.is_demo = 1
                """);
        jdbcTemplate.update("DELETE FROM sys_task WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_content_placement_cite WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_content_placement_item WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_content_placement WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_monitor_daily WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_year_target WHERE is_demo = 1");
        jdbcTemplate.update("DELETE FROM geo_topic WHERE is_demo = 1");
        // 同步清掉演示年区间的周/月/年快照，避免旧快照覆盖新日数据
        jdbcTemplate.update("""
                DELETE FROM geo_board_period_stat
                WHERE period_end >= '2014-01-01' AND period_start <= '2015-12-31'
                """);
        jdbcTemplate.update("""
                DELETE FROM geo_content_period_stat
                WHERE period_end >= '2014-01-01' AND period_start <= '2015-12-31'
                """);
        log.info("已物理清理旧演示数据及 2014-2015 看板快照");
    }

    private static String displayName(SysUser u) {
        if (u == null) {
            return "";
        }
        return StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
    }

    private record SeedCounts(int placements, int items, int cites, int tasks) {}
}
