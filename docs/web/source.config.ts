import { defineConfig } from 'fumadocs-mdx/config';

export default defineConfig({
    mdxOptions: {
        rehypeCodeOptions: {
            themes: {
                light: 'github-light-high-contrast',
                dark: 'github-dark-high-contrast',
            },
        },
    },
});
