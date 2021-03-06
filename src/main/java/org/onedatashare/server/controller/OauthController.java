package org.onedatashare.server.controller;

import org.onedatashare.server.service.OauthService;
import org.onedatashare.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;

import java.util.Map;

@Controller
@RequestMapping("/api/stork/oauth")
public class OauthController {
  @Autowired
  private UserService userService;

  @Autowired
  private OauthService oauthService;

//  @GetMapping
//  public Mono<RedirectView> handle(@RequestHeader HttpHeaders headers, @RequestParam Map<String, String> queryParameters) {
//    if(queryParameters.containsKey("state")) {
//      return userService.saveCredential(headers.getFirst("cookie"), oauthService.finish(queryParameters.get("code")))
//        .map(uuid -> new RedirectView("/oauth/" + uuid));
//    }
//    else {
//      return userService.userLoggedIn(headers.getFirst("cookie")).map(oauthService::redirectToDropboxAuth);
//    }
//  }

  @GetMapping
  public Object handle(@RequestHeader HttpHeaders headers, @RequestParam Map<String, String> queryParameters) {
    String cookie = headers.getFirst("cookie");
    if(queryParameters.containsKey("state")) {
      return userService.saveCredential(cookie, oauthService.finish(queryParameters.get("code")))
              .map(uuid -> Rendering.redirectTo("/oauth" + uuid).build());
    }
    else {
      return userService.userLoggedIn(cookie)
              .map(bool -> Rendering.redirectTo(oauthService.start()).build());
    }
  }
}
