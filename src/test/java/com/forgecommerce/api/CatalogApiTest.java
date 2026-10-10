package com.forgecommerce.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class CatalogApiTest {

  @Autowired private MockMvc mvc;

  @Test
  void productListIsPublicAndPaginated() throws Exception {
    String seller = token("seller1@example.com", "SELLER");
    long categoryId = createCategory(seller, "Electronics");
    createProduct(seller, "Phone", "100.00", categoryId);
    createProduct(seller, "Laptop", "200.00", categoryId);

    mvc.perform(get("/api/products?page=0&size=1&sort=price,desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("Laptop"));
  }

  @Test
  void customerCannotCreateProduct() throws Exception {
    String customer = token("cust1@example.com", "CUSTOMER");
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"X","price":10.00,"categoryId":1,"active":true}\
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedCreateReturns401() throws Exception {
    mvc.perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"X","price":10.00,"categoryId":1,"active":true}\
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidPriceReturns400() throws Exception {
    String seller = token("seller2@example.com", "SELLER");
    long categoryId = createCategory(seller, "Books");
    mvc.perform(
            post("/api/products")
                .header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Bad","price":-5.00,"categoryId":%d,"active":true}\
                    """
                    .formatted(categoryId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownProductReturns404() throws Exception {
    mvc.perform(get("/api/products/99999")).andExpect(status().isNotFound());
  }

  @Test
  void negativeStockIsRejected() throws Exception {
    String seller = token("seller3@example.com", "SELLER");
    long categoryId = createCategory(seller, "Toys");
    long productId = createProduct(seller, "Ball", "9.99", categoryId);
    mvc.perform(
            put("/api/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":-1}\
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void stockRoundTripAndSummary() throws Exception {
    String seller = token("seller4@example.com", "SELLER");
    long cat = createCategory(seller, "Grocery");
    long p1 = createProduct(seller, "Apple", "10.00", cat);
    long p2 = createProduct(seller, "Bread", "30.00", cat);
    setStock(seller, p1, 5);
    setStock(seller, p2, 7);

    mvc.perform(get("/api/products/" + p1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stock").value(5));

    mvc.perform(get("/api/products/analytics/summary").header("Authorization", "Bearer " + seller))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalProducts").value(2))
        .andExpect(jsonPath("$.totalStockUnits").value(12))
        .andExpect(jsonPath("$.byCategory.Grocery.products").value(2));
  }

  @Test
  void searchAndCategoryFilter() throws Exception {
    String seller = token("seller5@example.com", "SELLER");
    long phones = createCategory(seller, "Phones");
    long audio = createCategory(seller, "Audio");
    createProduct(seller, "Pixel Phone", "500.00", phones);
    createProduct(seller, "Headphones", "80.00", audio);

    mvc.perform(get("/api/products?query=phone"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
    mvc.perform(get("/api/products?query=phone&categoryId=" + audio))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  private String token(String email, String role) throws Exception {
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
    String body = result.getResponse().getContentAsString();
    return body.split("\"token\":\"")[1].split("\"")[0];
  }

  private long createCategory(String token, String name) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/categories")
                    .header("Authorization", "Bearer " + adminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"%s"}\
                        """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return Long.parseLong(result.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
  }

  private String adminToken() throws Exception {
    return token("admin" + System.nanoTime() + "@example.com", "ADMIN");
  }

  private long createProduct(String token, String name, String price, long categoryId)
      throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/products")
                    .header("Authorization", "Bearer " + token)
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

  private void setStock(String token, long productId, int qty) throws Exception {
    mvc.perform(
            put("/api/products/" + productId + "/stock")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":%d}\
                    """.formatted(qty)))
        .andExpect(status().isOk());
  }
}
