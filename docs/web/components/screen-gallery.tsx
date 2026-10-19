import { ImageZoom, Lightbox } from 'fumadocs-ui/components/image-zoom';
import { basePath } from '@/lib/base-path';

const screens = {
    welcome: {
        title: 'A warm welcome',
        description: 'Start with one action: Get started.',
        alt: 'Blobs welcome with Stay curious. Go anywhere. and a Get started button.',
    },
    'setup-model': {
        title: 'A model that fits',
        description:
            'Review the recommendation, size, and space before continuing.',
        alt: 'Model setup showing the recommended Qwen3 model, available space, and a comparison option.',
    },
    'setup-library': {
        title: 'Something to get you wondering',
        description:
            'The starter library gives your first chat sources to search.',
        alt: 'Starter library setup with English language, download size, and on-phone size.',
    },
    'setup-ready': {
        title: 'Ready to explore',
        description: 'Setup finishes before you enter your first chat.',
        alt: 'Blobs setup completion screen.',
    },
    home: {
        title: 'Follow your curiosity',
        description:
            'Ask a question, choose a model, or focus on particular sources.',
        alt: 'Blobs home with a question composer, model choice, and Sources button.',
    },
    library: {
        title: 'Your collections',
        description:
            'Browse what you’ve saved and choose what to use for answers.',
        alt: 'Installed collections in the Blobs library.',
    },
    discover: {
        title: 'Find more to take with you',
        description: 'Check the contents and size before starting a download.',
        alt: 'Discover more showing a library description and download action.',
    },
    reader: {
        title: 'Read the source',
        description:
            'Open the passage, origin, date, and license inside Blobs.',
        alt: 'Source details for Amed, with a passage, attribution, and full-source reading action.',
    },
    models: {
        title: 'Change your model',
        description: 'Model downloads and selection live together in Settings.',
        alt: 'Blobs Models screen showing available model choices.',
    },
    settings: {
        title: 'Make yourself comfortable',
        description:
            'Appearance, models, storage, privacy, and help in one place.',
        alt: 'Blobs Settings with appearance, models, storage, conversation data, privacy, help, and about.',
    },
};

export function ScreenGallery({ names }: { names: (keyof typeof screens)[] }) {
    return (
        <Lightbox>
            <div className="screen-gallery">
                {names.map((name) => {
                    const screen = screens[name];
                    return (
                        <figure key={name}>
                            <ImageZoom
                                src={`${basePath}/screens/${name}.webp`}
                                alt={screen.alt}
                                width={912}
                                height={2048}
                                sizes="(max-width: 480px) 320px, 350px"
                                caption={screen.title}
                            />
                            <figcaption>
                                <strong>{screen.title}</strong>
                                {screen.description}
                            </figcaption>
                        </figure>
                    );
                })}
            </div>
        </Lightbox>
    );
}
