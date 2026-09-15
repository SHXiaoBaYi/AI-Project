import { defineConfig, loadEnv } from 'vite';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  const base = env.VITE_BASE || '/';

  return {
    base,
    plugins: [react(), babel({ presets: [reactCompilerPreset()] }), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      host: '0.0.0.0',
      open: true,
      port: 3000,
      hmr: {
        overlay: false,
      },
      proxy: {
        '/api': {
          target: env.VITE_API_URL || 'http://127.0.0.1:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('error', (err) => {
              console.error('[vite proxy /api]', err.message);
            });
          },
        },
      },
    },
    css: {
      devSourcemap: true,
    },
    build: {
      cssCodeSplit: true,
      rollupOptions: {
        output: {
          // Vite 8 / Rolldown：vendor 与页面分包，未改动的 hash 文件可被浏览器长期缓存
          codeSplitting: {
            groups: [
              {
                name: 'react-vendor',
                test: /node_modules[\\/](react|react-dom|scheduler|react-router|react-redux|@reduxjs)[\\/]/,
                priority: 40,
              },
              {
                name: 'charts-vendor',
                test: /node_modules[\\/](@ant-design[\\/]charts|@antv)[\\/]/,
                priority: 35,
              },
              {
                name: 'antd-vendor',
                test: /node_modules[\\/](antd|@ant-design[\\/](cssinjs|colors|icons|fast-color)|@rc-component|rc-)[\\/]/,
                priority: 30,
              },
              {
                name: 'pro-vendor',
                test: /node_modules[\\/]@ant-design[\\/]pro-components[\\/]/,
                priority: 25,
              },
              {
                name: 'vendor',
                test: /node_modules[\\/]/,
                priority: 10,
              },
            ],
          },
        },
      },
    },
  };
});
