# CPay WordPress Website

This package adds a public WordPress marketing site to the CPay repository without changing the Spring Boot gateway or React merchant/admin portals.

## Packages

- `cpay-gateway-theme`: responsive CPay theme using the repository design tokens and visual language.
- `cpay-site-core`: structured CMS content, global links, plugin detection, reusable shortcodes and a public REST configuration endpoint.

## Installation

1. Install a current WordPress release with PHP 8.1 or newer.
2. Copy `cpay-gateway-theme` to `wp-content/themes/` and activate it.
3. Copy `cpay-site-core` to `wp-content/plugins/` and activate it.
4. Open **CPay Website** in WordPress admin and enter the live merchant registration, merchant portal and API documentation URLs.
5. Upload the approved CPay logo through **Appearance → Customize → Site Identity**.
6. Create the primary menu and assign it to **Primary navigation**.
7. Add Solutions, Industries, Integrations, FAQs, Testimonials and Resources through their CMS menus.
8. Install a supported forms plugin and paste its form shortcode into the CPay Website settings.

## Immediate plugin connections

The theme and core plugin are intentionally standards-based and avoid builder lock-in. They support Gutenberg, WooCommerce, ACF, Yoast SEO, Rank Math, Contact Form 7, WPForms, Gravity Forms, Elementor, Polylang, WPML, LiteSpeed Cache and WP Rocket. The CPay Website admin screen shows detected integration status.

## CMS API

Public site configuration and published solution/integration summaries are available at:

`/wp-json/cpay/v1/site-config`

This enables a future headless frontend, mobile app, chatbot or integration service to consume approved website content without scraping pages.

## Shortcodes

- `[cpay_get_started]`
- `[cpay_get_started label="Open a merchant account"]`
- `[cpay_signup_form]`
- `[cpay_integration_grid]`

## Security and performance

- All settings and content metadata are sanitized.
- The REST endpoint exposes only public configuration and published content.
- Theme JavaScript is dependency-free and loaded in the footer.
- CSS and markup use responsive layouts with accessible navigation and focus states.
- SEO metadata is added only when a major SEO plugin is not active.
- Images should be uploaded as optimized WebP/AVIF files with meaningful alt text.
