# Full-Stack E-Commerce Application

A comprehensive E-Commerce platform built with Spring Boot and React (Currently in Backend Development Phase).

## 🚀 Progress: Milestone 1 Completed (Days 1-14)

### Backend Technologies
* Java 17 + Spring Boot 3
* Spring Security + JWT Authentication
* Spring Data JPA + Hibernate
* MySQL Database

### ✅ Completed Features
- **User Authentication:** Registration, Login, and JWT Token generation.
- **Role-Based Access Control:** Configured `ADMIN` and `USER` roles.
- **Product Management:** CRUD operations for Products and Categories.
- **Shopping Cart:** 
  - Cart and CartItem entities linked to the User.
  - Add to cart logic with stock validation.
  - Price calculations.

### 📌 Core API Endpoints
* `POST /api/auth/register` - Register a new user
* `POST /api/auth/login` - Authenticate & get token
* `GET /api/products` - Fetch all products
* `POST /api/cart/items` - Add item to cart (Requires JWT)
* `GET /api/cart` - View user's cart (Requires JWT)

---
*Developed as part of a 30-Day Full-Stack Challenge.*
