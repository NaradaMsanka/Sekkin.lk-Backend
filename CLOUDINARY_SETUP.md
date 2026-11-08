# Cloudinary product images

New product images go from the React editor to the JWT-protected `POST /api/upload`
endpoint, then to Cloudinary. The backend returns an HTTPS URL. Saving the product
stores that URL in Supabase PostgreSQL through the existing product API.
There are no database schema changes. Existing product records are not migrated automatically.

The integration uses Cloudinary's signed REST API with Spring's HTTP client and
SHA-256 signatures. It requires no additional SDK dependency.
API reference: https://cloudinary.com/documentation/image_upload_api_reference
Signing reference: https://cloudinary.com/documentation/authentication_signatures

## Render configuration

In Render, select the backend web service, open **Environment**, and add:

| Name | Value |
| --- | --- |
| `CLOUDINARY_CLOUD_NAME` | `eey3dxc5` |
| `CLOUDINARY_API_KEY` | `924817243132744` |
| `CLOUDINARY_API_SECRET` | Your Cloudinary API secret |

Keep the existing Supabase datasource and JWT variables. Do not put Cloudinary
credentials in the React `.env` or in `VITE_*` variables. Backend `.env.example` is
only a template; Spring Boot does not automatically load a `.env` file. For local
backend development, add the same variables to the IDE's backend run configuration.

The frontend can keep `VITE_API_BASE_URL=https://sekkin-lk-backend.onrender.com`.
The updated backend must be deployed before using the updated upload editor with it.
No credentials are hardcoded in Java or React, and the API secret is not stored in the repository.

## Admin image management

- Sign in with an account whose backend JWT authority is `ADMIN`.
- Add or edit a product, select an image, edit it, and choose **Apply & Set Image**.
- Save the product to store the URL and ordered gallery in PostgreSQL.
- Use the image trash icon to remove an image, then save the product.
- Removed/replaced images from this integration are deleted from Cloudinary only
  after a successful product save, and only when no other product references the URL.
- Deleting a product also cleans up its unshared Cloudinary images after database deletion succeeds.
- Image uploads, product changes, and product deletion are protected on the backend.
  Public visitors can view images but cannot use the Admin upload/change operations.

The backend accepts JPEG, PNG, GIF, and WebP files up to 15 MB. Cloudinary also validates
the uploaded image. The frontend retains the 800x800 editor and gallery order.
Upload errors remain visible; no new images fall back to Base64 in the database.

## Existing images and cleanup limits

The old filesystem upload writer is removed. The read-only `/uploads/**` route and
legacy URL resolver remain so existing image references are not deliberately broken.
No local image files or existing product data are deleted. Files already missing
from Render cannot be recovered from their database URLs alone.

Before any deployment, back up recoverable Render uploads, local `Backend/uploads/`
files, and the product data. Moving old files to Cloudinary is a separate migration:
upload each recoverable file, verify the result, and update only its image references.
Keep main-image selection, gallery order, product IDs, and all other fields unchanged.

Cleanup only deletes URLs in this account's exact generated `sekkinlk/products/<UUID>`
namespace. Other URLs and legacy files are never deleted through Cloudinary cleanup.
If Cloudinary cleanup fails, product data stays saved and the backend logs the public
ID for a manual retry. There is no automatic retry queue. Images uploaded into an
unsaved or cancelled form can remain unused in Cloudinary; they can be reviewed in
the Cloudinary Media Library. Reference checking assumes serialized Admin updates;
coordinate simultaneous edits when reusing the same image across multiple products.

## Verification

Run backend tests without connecting to a database:

```powershell
mvn "-Dtest=CloudinaryServiceTest,ProductImageServiceTest,ProductServiceTest,ImageUploadSecurityTest,OrderServiceTest" test
```

The existing `OrderControllerTest` uses a full application context and expects a local
PostgreSQL database; it is not part of the isolated image checks above.

Run `npm run build` inside `Frontend/frontend`. After configuring and deploying the
backend, verify one Admin upload, product save, gallery reorder, saved-image removal,
and customer display. Confirm a normal user receives 403 for upload or product edits.
