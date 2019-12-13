/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.auth.mongo.test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.auth.mongo.AuthenticationException;
import io.vertx.ext.auth.mongo.MongoAuthentication;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Testing MongoAuth with no encryption for the user password
 *
 * @author mremme
 */

public class MongoAuthenticationNO_SALTTest extends MongoBaseTest {
  private static final Logger log = LoggerFactory.getLogger(MongoAuthenticationNO_SALTTest.class);

  private MongoAuthentication authenticationProvider;
  protected MongoAuthenticationOptions authenticationOptions = new MongoAuthenticationOptions();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getMongoClient();
  }

  @Before
  public void createDb() throws Exception {
    initTestUsers();
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }

  protected MongoAuthentication getAuthenticationProvider() {
    if (authenticationProvider == null) {

      try {
        authenticationProvider = MongoAuthentication.create(getMongoClient(), authenticationOptions);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return authenticationProvider;
  }

  @Test
  public void testAuthenticate() {
    JsonObject authInfo = new JsonObject();
    authInfo.put(authenticationOptions.getUsernameField(), "tim").put(authenticationOptions.getPasswordField(), "sausages");
    getAuthenticationProvider().authenticate(authInfo, onSuccess(user -> {
      assertNotNull(user);
      testComplete();
    }));
    await();
  }

  @Test
  public void testAuthenticateFailBadPwd() {
    JsonObject authInfo = new JsonObject();
    authInfo.put(authenticationOptions.getUsernameField(), "tim").put(authenticationOptions.getPasswordField(), "eggs");
    getAuthenticationProvider().authenticate(authInfo, onFailure(v -> {
      assertTrue(v instanceof AuthenticationException);
      testComplete();
    }));
    await();
  }

  @Test
  public void testAuthenticateFailBadUser() {
    JsonObject authInfo = new JsonObject();
    authInfo.put(authenticationOptions.getUsernameField(), "blah").put(authenticationOptions.getPasswordField(), "whatever");
    getAuthenticationProvider().authenticate(authInfo, onFailure(v -> {
      assertTrue(v instanceof AuthenticationException);
      testComplete();
    }));
    await();
  }

  /*
   * ################################################## preparation methods
   * ##################################################
   */
  private List<InternalUser> createUserList() {
    List<InternalUser> users = new ArrayList<>();
    users.add(new InternalUser("Michael", "ps1"));
    users.add(new InternalUser("Doublette", "ps1"));
    users.add(new InternalUser("Doublette", "ps2"));
    users.add(new InternalUser("Doublette", "ps2"));

    users.add(new InternalUser("tim", "sausages"));
    return users;
  }

  private void initTestUsers() throws Exception {
    log.info("initTestUsers");
    List<InternalUser> users = createUserList();
    CountDownLatch latch = new CountDownLatch(users.size());

    for (InternalUser user : users) {
      if (!initOneUser(user.username, user.password, latch))
        throw new InitializationError("could not create users");
    }
    awaitLatch(latch);
    if (!verifyUserData())
      throw new InitializationError("users weren't created");

  }

  private boolean verifyUserData() throws Exception {
    final StringBuffer buffer = new StringBuffer();
    CountDownLatch intLatch = new CountDownLatch(1);
    String collectionName = authenticationOptions.getCollectionName();
    log.info("verifyUserData in " + collectionName);
    getMongoClient().find(collectionName, new JsonObject(), res -> {
      if (res.succeeded()) {
        log.info(res.result().size() + " users found: " + res.result());

      } else {
        log.error("", res.cause());
        buffer.append("false");
      }
      intLatch.countDown();
    });
    awaitLatch(intLatch);
    return buffer.length() == 0;
  }

  public Future<String> insertUser(MongoAuthentication authenticationProvider, String username, String password) throws Exception {

    String hashedPassword = authenticationProvider.hash("pbkdf2", "somesalt", password);

    JsonObject user = new JsonObject();
    user.put(authenticationOptions.getUsernameField(), username);
    user.put(authenticationOptions.getPasswordField(), hashedPassword);

    Promise promise = Promise.promise();
    getMongoClient().save(authenticationOptions.getCollectionName(), user, promise);
    return promise.future();
  }

  /**
   * Creates a user inside mongo. Returns true, if user was successfully added
   *
   * @param latch
   * @return
   * @throws Exception
   * @throws Throwable
   */
  private boolean initOneUser(String username, String password, CountDownLatch latch) throws Exception {
    CountDownLatch intLatch = new CountDownLatch(1);
    final StringBuffer buffer = new StringBuffer();

    insertUser(getAuthenticationProvider(), username, password).setHandler(res -> {
      if (res.succeeded()) {
        log.info("user added: " + username);
        latch.countDown();
      } else {
        log.error("", res.cause());
        buffer.append("false");
      }
      intLatch.countDown();
    });
    awaitLatch(intLatch);
    return buffer.length() == 0;
  }

  private class InternalUser {
    String username;
    String password;

    InternalUser(String username, String password) {
      this.username = username;
      this.password = password;
    }

  }
}
