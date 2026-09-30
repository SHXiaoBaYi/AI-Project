package com.base.admin.service;

import com.base.admin.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 钉钉登录：OAuth 扫码（unionId）或企业内 H5 免登（userid）。
 */
@Service
@RequiredArgsConstructor
public class DingTalkAuthService {

    private final DingTalkAppService dingTalkAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public UserProfile resolveByAuthCode(String authCode) {
        DingTalkAppService.Credential credential = dingTalkAppService.credential();
        if (credential == null) {
            throw new BusinessException("钉钉应用未配置或未启用，无法扫码登录");
        }
        if (!StringUtils.hasText(authCode)) {
            throw new BusinessException("缺少钉钉授权码");
        }
        try {
            String userToken = exchangeUserAccessToken(credential, authCode.trim());
            return fetchContactMe(userToken);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("钉钉登录失败：" + ex.getMessage());
        }
    }

    /**
     * 企业内 H5 免登：JSAPI requestAuthCode → getuserinfo → 补全通讯录详情。
     */
    public CorpIdentity resolveByCorpAuthCode(String authCode) {
        DingTalkAppService.Credential credential = dingTalkAppService.credential();
        if (credential == null) {
            throw new BusinessException("钉钉应用未配置或未启用，无法免登");
        }
        if (!StringUtils.hasText(authCode)) {
            throw new BusinessException("缺少钉钉免登授权码");
        }
        try {
            String accessToken = appAccessToken(credential);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("code", authCode.trim());
            JsonNode json = postJson(
                    "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo?access_token="
                            + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                    body,
                    null);
            if (json.path("errcode").asInt(0) != 0) {
                String err = firstNonBlank(json.path("errmsg").asText(""), json.path("message").asText(""), "免登失败");
                throw new BusinessException("钉钉免登失败：" + err);
            }
            JsonNode result = json.path("result");
            String userid = firstNonBlank(result.path("userid").asText(""), result.path("userId").asText(""));
            if (!StringUtils.hasText(userid)) {
                throw new BusinessException("钉钉免登未返回 userid");
            }
            String unionId = firstNonBlank(
                    result.path("unionid").asText(""),
                    result.path("unionId").asText(""),
                    result.path("associated_unionid").asText(""));
            String nick = firstNonBlank(result.path("name").asText(""), result.path("nick").asText(""));
            String mobile = "";
            String avatar = "";
            try {
                ObjectNode get = objectMapper.createObjectNode();
                get.put("userid", userid);
                get.put("language", "zh_CN");
                JsonNode detail = postJson(
                        "https://oapi.dingtalk.com/topapi/v2/user/get?access_token="
                                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                        get,
                        null);
                if (detail.path("errcode").asInt(-1) == 0) {
                    JsonNode u = detail.path("result");
                    unionId = firstNonBlank(unionId,
                            u.path("unionid").asText(""),
                            u.path("unionId").asText(""));
                    nick = firstNonBlank(nick, u.path("name").asText(""), u.path("nickname").asText(""));
                    mobile = normalizeMobile(firstNonBlank(u.path("mobile").asText(""), u.path("telephone").asText("")));
                    avatar = firstNonBlank(u.path("avatar").asText(""), u.path("avatarUrl").asText(""));
                }
            } catch (Exception ignored) {
                // 详情失败仍可用 userid 建号/绑定
            }
            if (!StringUtils.hasText(unionId)) {
                unionId = "corp_" + userid;
            }
            return new CorpIdentity(userid, unionId, nick, mobile, avatar);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("钉钉免登失败：" + ex.getMessage());
        }
    }

    private String appAccessToken(DingTalkAppService.Credential credential) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appKey", credential.clientId());
        body.put("appSecret", credential.clientSecret());
        JsonNode json = postJson("https://api.dingtalk.com/v1.0/oauth2/accessToken", body, null);
        String token = json.path("accessToken").asText("");
        if (!StringUtils.hasText(token)) {
            String err = firstNonBlank(json.path("message").asText(""), "获取应用 accessToken 失败");
            throw new BusinessException(err);
        }
        return token;
    }

    private String exchangeUserAccessToken(DingTalkAppService.Credential credential, String authCode) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("clientId", credential.clientId());
        body.put("clientSecret", credential.clientSecret());
        body.put("code", authCode);
        body.put("grantType", "authorization_code");
        JsonNode json = postJson("https://api.dingtalk.com/v1.0/oauth2/userAccessToken", body, null);
        String accessToken = json.path("accessToken").asText("");
        if (!StringUtils.hasText(accessToken)) {
            String err = firstNonBlank(json.path("message").asText(""), json.path("errmsg").asText(""), "换取用户凭证失败");
            throw new BusinessException("钉钉授权失败：" + err);
        }
        return accessToken;
    }

    private UserProfile fetchContactMe(String userAccessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dingtalk.com/v1.0/contact/users/me"))
                .timeout(Duration.ofSeconds(15))
                .header("x-acs-dingtalk-access-token", userAccessToken)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode json = objectMapper.readTree(response.body() == null ? "{}" : response.body());
        if (response.statusCode() >= 400) {
            String err = firstNonBlank(json.path("message").asText(""), json.path("errmsg").asText(""), "HTTP " + response.statusCode());
            throw new BusinessException("读取钉钉用户失败：" + err);
        }
        String unionId = firstNonBlank(json.path("unionId").asText(""), json.path("unionid").asText(""));
        if (!StringUtils.hasText(unionId)) {
            throw new BusinessException("钉钉未返回 unionId，请确认应用已开通「个人手机号信息」等登录相关权限");
        }
        String openId = json.path("openId").asText("");
        String nick = firstNonBlank(json.path("nick").asText(""), json.path("nickName").asText(""));
        String mobile = firstNonBlank(json.path("mobile").asText(""), json.path("mobileNumber").asText(""));
        String avatar = json.path("avatarUrl").asText("");
        return new UserProfile(unionId, openId, nick, normalizeMobile(mobile), avatar);
    }

    private JsonNode postJson(String url, ObjectNode body, String userToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (StringUtils.hasText(userToken)) {
            builder.header("x-acs-dingtalk-access-token", userToken);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return objectMapper.readTree(response.body() == null ? "{}" : response.body());
    }

    private static String normalizeMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return "";
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public record UserProfile(String unionId, String openId, String nick, String mobile, String avatar) {
    }

    public record CorpIdentity(String userid, String unionId, String nick, String mobile, String avatar) {
        public UserProfile toProfile() {
            return new UserProfile(unionId, "", nick == null ? "" : nick, mobile == null ? "" : mobile,
                    avatar == null ? "" : avatar);
        }
    }
}
