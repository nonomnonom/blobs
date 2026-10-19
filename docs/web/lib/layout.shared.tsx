import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { Appearance } from '@/components/appearance';

export function baseOptions(): BaseLayoutProps {
    return {
        nav: {
            title: (
                <span className="brand">
                    blobs<span> / docs</span>
                </span>
            ),
        },
        githubUrl: 'https://github.com/nonomnonom/blobs',
        slots: { themeSwitch: Appearance },
        links: [
            { text: 'Guides', url: '/docs', active: 'url' },
            {
                text: 'Download app',
                url: 'https://github.com/nonomnonom/blobs/releases/latest',
                external: true,
            },
        ],
    };
}
