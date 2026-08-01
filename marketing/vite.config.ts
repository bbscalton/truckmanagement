import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

function githubPagesBase(): string {
  if (process.env.GITHUB_PAGES !== 'true') return '/'
  const repo =
    process.env.GITHUB_REPOSITORY?.split('/')[1] ??
    process.env.GITHUB_PAGES_REPO ??
    'truckmanagement'
  return `/${repo}/`
}

export default defineConfig({
  plugins: [react()],
  base: githubPagesBase(),
})
