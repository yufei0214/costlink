# CostLink - VPN报销服务系统

一个简单高效的VPN费用报销管理系统，支持LDAP认证、OCR金额识别、报销审批流程。

## 功能特性

- **LDAP认证**：集成公司LDAP，统一身份认证（开发模式支持模拟登录）
- **OCR识别**：自动识别购买截图中的金额（支持手动调整）
- **报销申请**：上传购买截图，填写VPN有效期，提交报销
- **审批流程**：管理员审核、确认、标记付款
- **统计仪表盘**：管理员查看申请数量、待办事项
- **批量导出**：导出已付款记录的图片压缩包

## 技术栈

- **前端**：Vue 3 + Vite + Element Plus + Pinia
- **后端**：Spring Boot 3.2 + MyBatis-Plus + Spring Security
- **数据库**：MySQL 8.0
- **OCR**：Tesseract
- **部署**：Docker Compose

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd CostLink
```

### 2. 一键启动

```bash
docker-compose up -d
```

### 3. 访问系统

- 前端：http://localhost
- 后端API：http://localhost:8080

### 4. 默认账号

- **管理员**：admin / admin123
- **普通用户**：任意用户名密码（首次登录自动创建）

## 项目结构

```
CostLink/
├── docker-compose.yml          # Docker编排
├── costlink-backend/           # Spring Boot后端
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── costlink-frontend/          # Vue3前端
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   └── src/
└── init/
    └── init.sql                # 数据库初始化
```

## 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| SPRING_DATASOURCE_URL | 数据库连接 | jdbc:mysql://mysql:3306/costlink |
| ADMIN_USERS | 管理员用户名列表 | admin |
| LDAP_ENABLED | 是否启用LDAP | false |
| LDAP_URL | LDAP服务器地址 | - |
| LDAP_BASE | LDAP BaseDN | - |

### 配置管理员

在 `docker-compose.yml` 中修改 `ADMIN_USERS` 环境变量，多个用户用逗号分隔：

```yaml
ADMIN_USERS: admin,zhangsan,lisi
```

## 使用流程

### 普通用户

1. 登录系统
2. 在「个人中心」设置支付宝收款账号
3. 在「报销申请」上传购买截图
4. 确认OCR识别金额（可手动调整）
5. 提交申请，等待审批

### 管理员

1. 在「仪表盘」查看统计数据
2. 在「报销管理」审核申请
3. 确认无误后点击「确认」
4. 手动支付宝转账后，点击「标记已付款」
5. 可批量选择已付款记录，导出图片压缩包

## 开发指南

### 本地开发

```bash
# 启动MySQL
docker-compose up -d mysql

# 后端开发
cd costlink-backend
mvn spring-boot:run

# 前端开发
cd costlink-frontend
npm install
npm run dev
```

### 构建部署

```bash
# 构建并启动所有服务
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```
