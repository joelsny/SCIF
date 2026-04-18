import { readFileSync } from "fs"
import { defineConfig } from "vitepress"

const scifGrammar = JSON.parse(readFileSync("./docs/.vitepress/c.tmLanguage.json", "utf-8"))

export default defineConfig({
    lang: 'en-US',
    title: 'SCIF',
    description: 'A secure smart contract language',
    base: '/SCIF/',
    lastUpdated: true,
    themeConfig: {
        sidebar: [
            {
                text: 'Introduction',
                collapsible: true,
                items: [
                    {
                        text: 'Getting Started',
                        link: '/Introduction/Your-First-SCIF-Contract'
                    },
                    {
                        text: 'SCIF by Example',
                        link: '/Introduction/scif-by-example'
                    }
                ]
            },
            {
                text: 'Language Basics',
                collapsible: true,
                items: [
                    {
                        text: 'Information Flow Control',
                        link: '/LanguageBasics/information-flow-control'
                    },
                    {
                        text: 'Types',
                        link: '/LanguageBasics/types'
                    },
                    {
                        text: 'Contracts',
                        link: '/LanguageBasics/contracts'
                    },
                    {
                        text: 'Exceptions and Failures',
                        link: '/LanguageBasics/exceptions-and-failures'
                    },
                    {
                        text: 'Expressions and Statements',
                        link: '/LanguageBasics/expressions-and-statements'
                    },
                    {
                        text: 'Built-in Methods and Variables',
                        link: '/LanguageBasics/built-in-methods-and-variables'
                    }
                ]
            },
            {
                text: 'Security Mechanisms',
                collapsible: true,
                items: [
                {
                    text: 'Label Model',
                    link: '/SecurityMechanisms/label-model'
                },
                {
                    text: 'Trust Delegation Mechanisms',
                    link: '/SecurityMechanisms/delegation'
                },
                {
                    text: 'Reentrancy Protection',
                    link: '/SecurityMechanisms/reentrancy'
                },
                {
                    text: 'Confused Deputy Protection',
                    link: '/SecurityMechanisms/confused-deputy'
                }
                ]
            }
        ]
    },
    markdown: {
        languages: [scifGrammar]
    }
})
