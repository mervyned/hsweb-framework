package org.hswebframework.web.oauth2.server.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.exception.UnAuthorizedException;
import org.hswebframework.web.oauth2.ErrorType;
import org.hswebframework.web.oauth2.OAuth2Exception;
import org.hswebframework.web.oauth2.server.AccessToken;
import org.hswebframework.web.oauth2.server.OAuth2Client;
import org.hswebframework.web.oauth2.server.OAuth2ClientManager;
import org.hswebframework.web.oauth2.server.OAuth2GrantService;
import org.hswebframework.web.oauth2.server.code.AuthorizationCodeRequest;
import org.hswebframework.web.oauth2.server.code.AuthorizationCodeTokenRequest;
import org.hswebframework.web.oauth2.server.credential.ClientCredentialRequest;
import org.hswebframework.web.oauth2.server.refresh.RefreshTokenRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/oauth2")
@AllArgsConstructor
@Tag(name = "OAuth2")
public class OAuth2AuthorizeController {

    private final OAuth2GrantService oAuth2GrantService;

    private final OAuth2ClientManager clientManager;

    @GetMapping(value = "/authorize", params = "response_type=code")
    @Operation(summary = "Request an authorization code and get the redirect address", parameters = {
            @Parameter(name = "client_id", required = true),
            @Parameter(name = "redirect_uri", required = true),
            @Parameter(name = "state"),
            @Parameter(name = "response_type", description = "固定值为code")
    })
    public Mono<String> authorizeByCode(ServerWebExchange exchange) {
        Map<String, String> param = new HashMap<>(exchange.getRequest().getQueryParams().toSingleValueMap());

        return Authentication
                .currentReactive()
                .switchIfEmpty(Mono.error(UnAuthorizedException::new))
                .flatMap(auth -> this
                        .getOAuth2Client(param.get("client_id"))
                        .flatMap(client -> {
                            String redirectUri = param.getOrDefault("redirect_uri", client.getRedirectUrl());
                            client.validateRedirectUri(redirectUri);
                            return oAuth2GrantService
                                    .authorizationCode()
                                    .requestCode(new AuthorizationCodeRequest(client, auth, param))
                                    .doOnNext(response -> {
                                        Optional
                                                .ofNullable(param.get("state"))
                                                .ifPresent(state -> response.with("state", state));
                                    })
                                    .map(response -> buildRedirect(redirectUri, response.getParameters()));
                        }));
    }

    @GetMapping(value = "/token")
    @Operation(summary = "(GET)token", parameters = {
            @Parameter(name = "client_id"),
            @Parameter(name = "client_secret"),
            @Parameter(name = "code", description = "grantType为authorization_code时不能为空"),
            @Parameter(name = "grant_type", schema = @Schema(implementation = GrantType.class))
    })
    @Authorize(ignore = true)
    public Mono<ResponseEntity<AccessToken>> requestTokenByCode(
            @RequestParam("grant_type") GrantType grantType,
            ServerWebExchange exchange) {
        Map<String, String> params = exchange.getRequest().getQueryParams().toSingleValueMap();
        Tuple2<String,String> clientIdAndSecret = getClientIdAndClientSecret(params,exchange);
        return this
                .getOAuth2Client(clientIdAndSecret.getT1())
                .doOnNext(client -> client.validateSecret(clientIdAndSecret.getT2()))
                .flatMap(client -> grantType.requestToken(oAuth2GrantService, client, new HashMap<>(params)))
                .map(ResponseEntity::ok);
    }


    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "(POST)token", parameters = {
            @Parameter(name = "client_id"),
            @Parameter(name = "client_secret"),
            @Parameter(name = "code", description = "grantType为authorization_code时不能为空"),
            @Parameter(name = "grant_type", schema = @Schema(implementation = GrantType.class))
    })
    @Authorize(ignore = true)
    public Mono<ResponseEntity<AccessToken>> requestTokenByCode(ServerWebExchange exchange) {
        return exchange
                .getFormData()
                .map(MultiValueMap::toSingleValueMap)
                .flatMap(params -> {
                    Tuple2<String,String> clientIdAndSecret = getClientIdAndClientSecret(params,exchange);
                    GrantType grantType = GrantType.of(params.get("grant_type"));
                    return this
                            .getOAuth2Client(clientIdAndSecret.getT1())
                            .doOnNext(client -> client.validateSecret(clientIdAndSecret.getT2()))
                            .flatMap(client -> grantType.requestToken(oAuth2GrantService, client, new HashMap<>(params)))
                            .map(ResponseEntity::ok);
                });
    }

    private Tuple2<String, String> getClientIdAndClientSecret(Map<String, String> params, ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Basic ")) {
            String[] arr = new String(Base64.decodeBase64(authorization.substring(5))).split(":");
            if (arr.length >= 2) {
                return Tuples.of(arr[0], arr[1]);
            }
            return Tuples.of(arr[0], arr[0]);
        }
        return Tuples.of(params.getOrDefault("client_id",""),params.getOrDefault("client_secret",""));
    }

    public enum GrantType {
        authorization_code {
            @Override
            Mono<AccessToken> requestToken(OAuth2GrantService service, OAuth2Client client, Map<String, String> param) {
                return service
                        .authorizationCode()
                        .requestToken(new AuthorizationCodeTokenRequest(client, param));
            }
        },
        client_credentials {
            @Override
            Mono<AccessToken> requestToken(OAuth2GrantService service, OAuth2Client client, Map<String, String> param) {
                return service
                        .clientCredential()
                        .requestToken(new ClientCredentialRequest(client, param));
            }
        },
        refresh_token {
            @Override
            Mono<AccessToken> requestToken(OAuth2GrantService service, OAuth2Client client, Map<String, String> param) {
                return service
                        .refreshToken()
                        .requestToken(new RefreshTokenRequest(client, param));
            }
        };

        abstract Mono<AccessToken> requestToken(OAuth2GrantService service, OAuth2Client client, Map<String, String> param);

        static GrantType of(String name) {
            try {
                return GrantType.valueOf(name);
            } catch (Throwable e) {
                throw new OAuth2Exception(ErrorType.UNSUPPORTED_GRANT_TYPE);
            }
        }
    }

    @SneakyThrows
    public static String urlEncode(String url) {
        return URLEncoder.encode(url, "utf-8");
    }

    static String buildRedirect(String redirectUri, Map<String, Object> params) {
        String paramsString = params.entrySet()
                                    .stream()
                                    .map(e -> e.getKey() + "=" + urlEncode(String.valueOf(e.getValue())))
                                    .collect(Collectors.joining("&"));
        if (redirectUri.contains("?")) {
            return redirectUri + "&" + paramsString;
        }
        return redirectUri + "?" + paramsString;
    }

    private Mono<OAuth2Client> getOAuth2Client(String id) {
        return clientManager
                .getClient(id)
                .switchIfEmpty(Mono.error(() -> new OAuth2Exception(ErrorType.ILLEGAL_CLIENT_ID)));
    }
}
