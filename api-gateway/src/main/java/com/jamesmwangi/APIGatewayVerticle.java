package com.jamesmwangi;

import com.sun.prism.impl.Disposer;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2API;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.util.List;

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
    router.get("/api/v").handler(this::apiVersion);
    
    // create oauth2 instance for keycloak
    oauth2 = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, config());
    router.route().handler(UserSessionHandler.create(oauth2));
    
    String hostURI = buildHostURI();
    
    // set auth callback handler
    router.route("/callback").handler(context -> authCallback(oauth2, hostURI, context));
    
    router.get("/uaa").handler(this::authUaaHandler);
    router.get("/login").handler(this::loginEntryHandler);
    router.post("/logout").handler(this::logoutHandler);

    // api dispatcher
    router.route("/api/*").handler(this::dispatchRequests);


    // init heart beat check
    initHealthCheck();

    //static content
    router.route("/*").handler(StaticHandler.create());

    // enable HTTPS
    HttpServerOptions httpServerOptions = new HttpServerOptions()
        .setSsl(true)
        .setKeyStoreOptions(new JksOptions().setPath("server.jks").setPassword("123456"));

    vertx.createHttpServer(httpServerOptions)
        .requestHandler(router::accept)
        .listen(port, host,ar->{
          if(ar.succeeded()){
            publishApiGateway(host, port);
            future.complete();
            logger.info("API Gateway in running on port " + port);

            //publish log
            publishLogGateway("api_getway_init_success:" + port );
          } else {
            future.fail(ar.cause());
          }
        });
  }

  protected void enableLocalSession(Router router){
    router.route().handler(CookieHandler.create());
    router.route().handler(SessionHandler.create(
        LocalSessionStore.create(vertx, "shopping.user.session")
    ));
  }

  private void dispatchRequests(RoutingContext context){
    int initialOffset = 5; //length of /api/

    // run with circuit breaker in order to deal with failure
    circuitBreaker.execute(future -> {
      getAllEndPoints().setHandler(ar -> {
        if(ar.succeeded()){
          List<Record> recordList = ar.result();


          // get relative path and relative prefix to dispatch client
          String path = context.request().uri();

          if(path.length() <= initialOffset){
            notFound(context);
            future.complete();
            return;
          }

          String prefix = (path.substring(initialOffset));
          String path



        }
      })
    })
  }

  /**
   * Get all REST endpoints from the service discovery infrastructure
   *
   * @Return asyc result
   * */
  private Future<List<Record>> getAllEndPoints(){
    Future<List<Record>> future = Future.future();
    discovery.getRecords(record -> record.getType().equals(HttpEndpoint.TYPE),
        future.completer());
    return future;
  }


}
