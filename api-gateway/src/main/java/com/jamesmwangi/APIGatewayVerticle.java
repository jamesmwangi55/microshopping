package com.jamesmwangi;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2API;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.UserSessionHandler;

public class APIGatewayVerticle extends BaseMicroserviceVerticle {

  private static final int DEFAULT_PORT = 8787;

  private static final Logger logger = LoggerFactory.getLogger(APIGatewayVerticle.class);
  
  private OAuth2Auth oauth2;

  @Override
  public void start(Future<Void> future) throws Exception {
    super.start();
    
    // get HTTP host and port from configuration or use default value
    String host = config().getString("api.gateway.http.address", "localhost");
    int port = config().getInteger("api.gateway.http.port", DEFAULT_PORT);
    
    Router router = Router.router(vertx);
    
    // cookie and session handler
    enableLocalSession(router);
    
    //body handler
    router.route().handler(BodyHandler.create());
    
    //version handler
    router.get("/api/v").handler(this::thisApiVersion);
    
    // create oauth2 instance for keycloak
    oauth2 = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, config());
    router.route().handler(UserSessionHandler.create(oauth2));
    
    String hostURI = buildHostURI();
    
    // set auth callback handler
    router.route("/callback").handler(context -> authCallback(oauth2, hostURI, context));
    
    router.get("/uaa").handler(this::authUaaHandler);
    router.get("/login").handler(this::loginEntryHandler);
    // TODO: 7/5/17 resume from here 
  }
}
