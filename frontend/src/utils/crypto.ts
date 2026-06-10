import CryptoJS from 'crypto-js'

// 从环境变量获取密钥
// 修复（M2）：不允许使用默认值，缺失时启动报错，避免生产环境误用
const SECRET_KEY = import.meta.env.VITE_TOKEN_SECRET
if (!SECRET_KEY) {
  throw new Error(
    '缺少 VITE_TOKEN_SECRET 环境变量！请在 .env 文件中配置（至少 32 字符的随机串）'
  )
}
if (SECRET_KEY.length < 32) {
  throw new Error('VITE_TOKEN_SECRET 长度必须 >= 32 字符')
}

/**
 * 加密数据
 */
export function encrypt(data: string): string {
  try {
    return CryptoJS.AES.encrypt(data, SECRET_KEY).toString()
  } catch (error) {
    console.error('加密失败', error)
    return data
  }
}

/**
 * 解密数据
 */
export function decrypt(encryptedData: string): string | null {
  try {
    const bytes = CryptoJS.AES.decrypt(encryptedData, SECRET_KEY)
    const originalText = bytes.toString(CryptoJS.enc.Utf8)
    return originalText || null
  } catch (error) {
    console.error('解密失败', error)
    return null
  }
}
