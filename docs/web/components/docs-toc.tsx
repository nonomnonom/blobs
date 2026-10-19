'use client';

import { TOCPopover } from 'fumadocs-ui/layouts/docs/page/slots/toc';
import type { ComponentProps } from 'react';

export function DocsTOC(props: ComponentProps<typeof TOCPopover>) {
    return (
        <nav className="contents xl:hidden" aria-label="On this page">
            <TOCPopover {...props} />
        </nav>
    );
}
