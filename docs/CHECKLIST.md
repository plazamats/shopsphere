# Feature checklist (Version 1)

Status is based on what was actually run. "Tested" means an automated test or a manual run passed in this build.

## Completed
- Registration, login, logout, hashing, profile, password change, suspend and reactivate, RBAC (tested)
- Product management, archive, search, category filter, categories, image references (tested)
- Cart, wishlist, reviews with verified purchase (tested)
- Checkout transaction, order numbers, history, status changes, cancellation, receipts (tested)
- Inventory, stock adjustments, movement history, low stock alerts, no negative stock (tested)
- Simulated payments, failure, references, duplicate protection (tested)
- Admin dashboard, customers, orders, inventory, payments, reports, audit log (server tested, UI screenshots reviewed)
- Super admin: administrators, roles, settings (tested), no self-granting SUPER_ADMIN (tested)
- Concurrent last-unit purchase and multi-client sockets (tested)
- Swing client for all of the above, run against the live server under a virtual display

## Incomplete or partial
- Client flows were checked by screenshot of every main screen, not by automated UI tests
- No image upload or display, references only

## Blocked or not verified in this build
- Maven could not download plugins in the build environment, so `mvn test` was **not** run. Sources were compiled with `javac --release 17` and the poms were checked with `mvn validate`. Please run `mvn clean package` on your machine.
- Tests were executed with a small stand-in runner for the JUnit 5 API (JUnit itself was not downloadable). The test sources use standard JUnit 5 annotations and assertions.
- BCrypt was replaced by the JDK PBKDF2 implementation (no extra library available).
