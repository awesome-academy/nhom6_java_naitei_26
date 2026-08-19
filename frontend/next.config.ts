import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: 'http',
        hostname: 'localhost',
        port: '9000',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: process.env.NEXT_PUBLIC_MINIO_HOST || 'minio.example.com',
        pathname: '/**',
      },
    ],
  },
};

export default nextConfig;
