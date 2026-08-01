import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'

const base = process.env.GITHUB_PAGES === 'true' ? '/truckmanagement/' : '/'

export default defineConfig({
  plugins: [react()],
  base,
  resolve: {
    alias: {
      '@marketing-tcd': resolve(__dirname, '../marketing/src/tcd'),
    },
  },
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        tcd: resolve(__dirname, 'tcd.html'),
      },
    },
  },
})
