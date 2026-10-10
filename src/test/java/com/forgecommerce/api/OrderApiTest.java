package com.forgecommerce.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void fullCheckoutFlowWithPaymentSuccess() throws Exception {
    Fixture fx = fixture("flow1");
    addToCart(fx.customer, fx.productId, 2);

    long orderId = placeOrder(fx.customer, "key-flow-1", true);
    stockIs(fx.productId, 3); // 5 - 2

    // Payment success moves order to CONFIRMED.
    mvc.perform(
            post("/api/orders/" + orderId + "/payment/simulate")
                .header("Authorization", "Bearer " + fx.customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"outcome":"SUCCESS"}\
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));
  }

  @Test
  void idempotentRetryReturnsSameOrder() throws Exception {
    Fixture fx = fixture("flow2");
    addToCart(fx.customer, fx.productId, 1);

    long first = placeOrder(fx.customer, "same-key", true);
    long second = placeOrder(fx.customer, "same-key", false);
    assert first == second;
    stockIs(fx.productId, 4); // decremented exactly once
  }

  @Test
  void oversellIsRejected() throws Exception {
    Fixture fx = fixture("flow3");
    // Cart validation caps at stock…
    mvc.perform(
            post("/api/cart/items")
                .header("Authorization", "Bearer " + fx.customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId":%d,"quantity":99}\
                    """.formatted(fx.productId)))
        .andExpect(status().isConflict());
  }

  @Test
  void paymentFailureCancelsAndRestocks() throws Exception {
    Fixture fx = fixture("flow4");
    addToCart(fx.customer, fx.productId, 2);
    long orderId = placeOrder(fx.customer, "key-flow-4", true);

    mvc.perform(
            post("/api/orders/" + orderId + "/payment/simulate")
                .header("Authorization", "Bearer " + fx.customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"outcome":"FAILED"}\
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    stockIs(fx.productId, 5); // restored
  }

  @Test
  void illegalTransitionReturns422() throws Exception {
    Fixture fx = fixture("flow5");
    addToCart(fx.customer, fx.productId, 1);
    long orderId = placeOrder(fx.customer, "key-flow-5", true);

    // PENDING -> SHIPPED skips states: rejected.
    mvc.perform(
            patch("/api/orders/" + orderId + "/status")
                .header("Authorization", "Bearer " + fx.seller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"SHIPPED"}\
                    """))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void cancelRestoresStock() throws Exception {
    Fixture fx = fixture("flow6");
    addToCart(fx.customer, fx.productId, 2);
    long orderId = placeOrder(fx.customer, "key-flow-6", true);
    stockIs(fx.productId, 3);

    mvc.perform(
            post("/api/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + fx.customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    stockIs(fx.productId, 5);
  }

  @Test
  void cartsAndOrdersRequireAuth() throws Exception {
    mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idempotencyKey":"x"}\
                    """))
        .andExpect(status().isUnauthorized());
  }

  private record Fixture(String customer, String seller, long productId) {}

  private Fixture fixture(String tag) throws Exception {
    String seller = register("seller-" + tag + "@example.com", "SELLER");
    String customer = register("cust-" + tag + "@example.com", "CUSTOMER");
    String admin = register("admin-" + tag + "@example.com", "ADMIN");
    long categoryId = createCategory(admin, "Cat-" + tag);
    long productId = createProduct(seller, "Widget-" + tag, "25.00", categoryId);
    setStock(seller, productId, 5);
    return new Fixture(customer, seller, productId);
  }

  private String register(String email, String role) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"email":"%s","password":"password123","role":"%s"}\
                        """
                        .formatted(email, role)))
            .andExpect(status().isCreated())
            .andReturn();
    return result.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
  }

  private long createCategory(String adminToken, String name) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/categories")
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"%s"}\
                        """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return Long.parseLong(result.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
  }

  private long createProduct(String sellerToken, String name, String price, long categoryId)
      throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/products")
                    .header("Authorization", "Bearer " + sellerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","price":%s,"categoryId":%d,"active":true}\
                        """
                        .formatted(name, price, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
    return Long.parseLong(result.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
  }

  private void setStock(String sellerToken, long productId, int qty) throws Exception {
    mvc.perform(
            put("/api/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":%d}\
                    """.formatted(qty)))
        .andExpect(status().isOk());
  }

  private void addToCart(String customerToken, long productId, int qty) throws Exception {
    mvc.perform(
            post("/api/cart/items")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"productId":%d,"quantity":%d}\
                    """
                    .formatted(productId, qty)))
        .andExpect(status().isOk());
  }

  private long placeOrder(String customerToken, String key, boolean expectCreated) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/orders")
                    .header("Authorization", "Bearer " + customerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"idempotencyKey":"%s"}\
                        """.formatted(key)))
            .andExpect(expectCreated ? status().isCreated() : status().isOk())
            .andReturn();
    return Long.parseLong(result.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
  }

  private void stockIs(long productId, int expected) throws Exception {
    mvc.perform(get("/api/products/" + productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(expected));
  }
}
