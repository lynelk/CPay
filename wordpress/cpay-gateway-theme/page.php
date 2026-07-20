<?php
/** Page template. @package CPay_Gateway */
defined( 'ABSPATH' ) || exit;
get_header();
?>
<main id="main-content" class="cpay-entry"><div class="cpay-container">
<?php while ( have_posts() ) : the_post(); ?><article <?php post_class(); ?>><header class="cpay-entry__header"><span class="cpay-eyebrow"><?php echo esc_html( get_bloginfo( 'name' ) ); ?></span><h1><?php the_title(); ?></h1></header><div class="cpay-entry__content"><?php the_content(); wp_link_pages(); ?></div></article><?php endwhile; ?>
</div></main>
<?php get_footer(); ?>
