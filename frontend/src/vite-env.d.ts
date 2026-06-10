// Vite 客户端类型（让 import.meta.env / import.meta.glob 可用）
/// <reference types="vite/client" />

// 允许使用 .vue 单文件组件的导入
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// crypto-js 缺类型（已 npm i @types/crypto-js，但兜底声明）
declare module 'crypto-js' {
  const CryptoJS: any
  export default CryptoJS
  export const AES: any
  export const enc: any
}
