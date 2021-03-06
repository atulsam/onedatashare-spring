package org.onedatashare.server.module.dropbox;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import org.onedatashare.server.model.core.Credential;
import org.onedatashare.server.model.core.Session;
import org.onedatashare.server.model.credential.OAuthCredential;
import org.onedatashare.server.model.error.AuthenticationRequired;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.file.Path;

public class DbxSession extends Session<DbxSession, DbxResource> {
  DbxClientV2 client;

  public DbxSession(URI uri, Credential cred) {
    super(uri, cred);
  }

  @Override
  public Mono<DbxResource> select(String path) {
    return Mono.just(new DbxResource(this, path));
  }

  @Override
  public Mono<DbxSession> initialize() {
    return Mono.create(s -> {
      if(credential instanceof OAuthCredential){
        OAuthCredential oauth = (OAuthCredential) credential;
        DbxRequestConfig config =
                DbxRequestConfig.newBuilder("OneDataShare-DIDCLab").build();
        client = new DbxClientV2(config, oauth.token);
        s.success(this);
      }
      else s.error(new AuthenticationRequired("oauth"));
    });
  }
}
