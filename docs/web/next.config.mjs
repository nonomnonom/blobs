import { createMDX } from 'fumadocs-mdx/next';
import { fileURLToPath } from 'node:url';

const withMDX = createMDX();

/** @type {import('next').NextConfig} */
export default withMDX({
    output: 'export',
    basePath: process.env.NEXT_PUBLIC_BASE_PATH || '',
    trailingSlash: true,
    reactStrictMode: true,
    images: { unoptimized: true },
    turbopack: { root: fileURLToPath(new URL('../..', import.meta.url)) },
});
