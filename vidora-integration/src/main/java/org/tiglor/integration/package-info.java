/**
 * 平台外部系统的适配层（防腐层）。
 *
 * <p>放这里：云转码（阿里云 MPS / 腾讯云 MPS）、内容安全审核、短信、支付、CDN 刷新等**平台之外**的
 * HTTP/RPC 客户端，连同它们的请求/响应模型、超时、重试与降级。OpenFeign 保留在这条线上，
 * 因为外部服务不会给 Java 接口，契约只能靠 HTTP + OpenAPI 描述。
 *
 * <p>不放这里：vidora 内部服务之间的调用，那些走 {@code vidora-api-{服务名}} 的共享契约。
 * 判据是一句话——<b>对端是否和你在同一个仓库、同一次发布</b>：是则内部，否则第三方。
 *
 * <p>包结构约定 {@code org.tiglor.integration.{provider}}，每个 provider 一个子包，
 * 外部模型必须在**本层**转换成内部模型再出去，别让业务服务 import 到第三方 DTO——
 * 那等于把对方的字段变更变成全仓库的破坏性改动。
 *
 * <p>业务服务在真正需要某个外部调用之前，不要在自己的 pom 里引本模块（与
 * docs/ARCHITECTURE.md 6.2.1「不为没有调用需求的服务强行引入依赖」一致）。
 */
package org.tiglor.integration;
