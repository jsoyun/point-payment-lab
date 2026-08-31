#!/usr/bin/env python3
import os
from pathlib import Path
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, white
from reportlab.lib.utils import simpleSplit

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "point-payment-lab-portfolio.pdf"
W, H = landscape(A4)

FONT_REGULAR = "/System/Library/Fonts/Supplemental/AppleGothic.ttf"
FONT_BOLD = os.getenv(
    "PORTFOLIO_BOLD_FONT",
    str(Path.home() / "Library" / "Fonts" / "JalnanGothicTTF.ttf"),
)
if not Path(FONT_BOLD).is_file():
    FONT_BOLD = FONT_REGULAR
pdfmetrics.registerFont(TTFont("Korean", FONT_REGULAR))
pdfmetrics.registerFont(TTFont("KoreanBold", FONT_BOLD))

NAVY = HexColor("#17233C")
BLUE = HexColor("#2563EB")
BLUE_LIGHT = HexColor("#EAF2FF")
# 표지의 네이비·블루·퍼플·그린 계열만 전체 문서에서 사용한다.
# 문제/실패 강조는 퍼플, 외부 경계·DB 강조는 블루로 통일한다.
RED = HexColor("#7C3AED")
RED_LIGHT = HexColor("#F1EBFF")
GREEN = HexColor("#16A34A")
GREEN_LIGHT = HexColor("#EAF8EF")
ORANGE = HexColor("#2563EB")
ORANGE_LIGHT = HexColor("#EAF2FF")
PURPLE = HexColor("#7C3AED")
PURPLE_LIGHT = HexColor("#F1EBFF")
GRAY_900 = HexColor("#1F2937")
GRAY_700 = HexColor("#4B5563")
GRAY_500 = HexColor("#6B7280")
GRAY_300 = HexColor("#D1D5DB")
GRAY_100 = HexColor("#F3F4F6")
BG = HexColor("#F8FAFC")


def rounded(c, x, y, w, h, fill, stroke=GRAY_300, radius=10, width=1):
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(width)
    c.roundRect(x, y, w, h, radius, fill=1, stroke=1)


def text(c, value, x, y, size=11, color=GRAY_900, font="Korean", max_width=None,
         leading=None, align="left"):
    c.setFont(font, size)
    c.setFillColor(color)
    leading = leading or size * 1.45
    lines = simpleSplit(value, font, size, max_width) if max_width else value.split("\n")
    cursor = y
    for line in lines:
        if align == "center":
            c.drawCentredString(x, cursor, line)
        elif align == "right":
            c.drawRightString(x, cursor, line)
        else:
            c.drawString(x, cursor, line)
        cursor -= leading
    return cursor


def badge(c, label, x, y, fill=GREEN, width=None):
    width = width or pdfmetrics.stringWidth(label, "KoreanBold", 8.5) + 18
    rounded(c, x, y, width, 20, fill, fill, radius=10)
    text(c, label, x + width / 2, y + 6, 8.5, white, "KoreanBold", align="center")
    return width


def footer(c, page, section):
    c.setStrokeColor(GRAY_300)
    c.line(42, 28, W - 42, 28)
    text(c, "POINT PAYMENT LAB", 42, 13, 7.5, GRAY_500, "KoreanBold")
    text(c, section, W / 2, 13, 7.5, GRAY_500, align="center")
    text(c, f"{page:02d} / 18", W - 42, 13, 7.5, GRAY_500, "KoreanBold", align="right")


def page_title(c, page, section, title_value, subtitle=None, badge_label="IMPLEMENTED"):
    c.setFillColor(BG)
    c.rect(0, 0, W, H, fill=1, stroke=0)
    badge(c, badge_label, 42, H - 54, GREEN if badge_label == "IMPLEMENTED" else PURPLE)
    text(c, title_value, 42, H - 91, 23, NAVY, "KoreanBold")
    if subtitle:
        text(c, subtitle, 42, H - 116, 10.5, GRAY_500, max_width=W - 84)
    footer(c, page, section)


def card(c, x, y, w, h, title_value, body, accent=BLUE, fill=white, title_size=12, body_size=9.5):
    rounded(c, x, y, w, h, fill, GRAY_300, 10)
    c.setFillColor(accent)
    c.roundRect(x, y, 6, h, 3, fill=1, stroke=0)

    # 낮은 안내 바는 제목과 본문을 가로로 배치해 배경 밖으로 내려가지 않게 한다.
    if h <= 55:
        safe_title_size = min(title_size, 9.5)
        title_width = pdfmetrics.stringWidth(title_value, "KoreanBold", safe_title_size)
        title_block = min(max(title_width + 28, 105), w * 0.34)
        text(c, title_value, x + 18, y + h / 2 - 3, safe_title_size, NAVY, "KoreanBold")

        body_x = x + title_block
        body_width = w - title_block - 16
        fitted_body_size = min(body_size, 8)
        while fitted_body_size >= 6.5:
            body_lines = simpleSplit(body, "Korean", fitted_body_size, body_width)
            body_leading = fitted_body_size * 1.35
            if len(body_lines) * body_leading <= h - 10:
                break
            fitted_body_size -= 0.3
        body_top = y + h / 2 + ((len(body_lines) - 1) * body_leading) / 2 - 3
        text(c, "\n".join(body_lines), body_x, body_top, fitted_body_size, GRAY_700,
             leading=body_leading)
        return

    title_lines = simpleSplit(title_value, "KoreanBold", title_size, w - 34)
    title_leading = title_size * 1.25
    title_top = y + h - 27
    text(c, "\n".join(title_lines), x + 18, title_top, title_size, NAVY,
         "KoreanBold", leading=title_leading)

    body_top = title_top - len(title_lines) * title_leading - 8
    available_height = body_top - (y + 10)
    fitted_body_size = body_size
    while fitted_body_size >= 6.8:
        body_lines = simpleSplit(body, "Korean", fitted_body_size, w - 34)
        body_leading = fitted_body_size * 1.45
        if len(body_lines) * body_leading <= available_height:
            break
        fitted_body_size -= 0.3
    text(c, "\n".join(body_lines), x + 18, body_top, fitted_body_size, GRAY_700,
         leading=body_leading)


def metric(c, x, y, w, label, before, after, color=GREEN):
    rounded(c, x, y, w, 77, white, GRAY_300, 10)
    text(c, label, x + 16, y + 55, 9, GRAY_500, "KoreanBold")
    text(c, before, x + 16, y + 22, 18, RED, "KoreanBold")
    text(c, "→", x + w / 2, y + 25, 14, GRAY_500, "KoreanBold", align="center")
    text(c, after, x + w - 16, y + 22, 18, color, "KoreanBold", align="right")


def arrow(c, x1, y1, x2, y2, color=BLUE, label=None):
    c.setStrokeColor(color)
    c.setFillColor(color)
    c.setLineWidth(2)
    c.line(x1, y1, x2, y2)
    angle = 5
    c.line(x2, y2, x2 - angle, y2 + angle)
    c.line(x2, y2, x2 - angle, y2 - angle)
    if label:
        text(c, label, (x1 + x2) / 2, y1 + 8, 7.5, GRAY_500, align="center")


def flow_box(c, x, y, w, h, title_value, body="", color=BLUE, light=BLUE_LIGHT):
    rounded(c, x, y, w, h, light, color, 10, 1.3)
    text(c, title_value, x + w / 2, y + h - 24, 10, color, "KoreanBold", align="center")
    if body:
        text(c, body, x + w / 2, y + h - 44, 8, GRAY_700, max_width=w - 20, align="center", leading=11)


def table(c, x, y_top, widths, rows, row_h=30, header_fill=NAVY):
    y = y_top
    total = sum(widths)
    for ridx, row in enumerate(rows):
        fill = header_fill if ridx == 0 else (white if ridx % 2 else GRAY_100)
        c.setFillColor(fill)
        c.setStrokeColor(GRAY_300)
        c.rect(x, y - row_h, total, row_h, fill=1, stroke=1)
        cx = x
        for idx, value in enumerate(row):
            if idx:
                c.line(cx, y - row_h, cx, y)
            color = white if ridx == 0 else GRAY_700
            font = "KoreanBold" if ridx == 0 or idx == 0 else "Korean"
            text(c, str(value), cx + 8, y - 19, 8.5, color, font, max_width=widths[idx] - 16)
            cx += widths[idx]
        y -= row_h
    return y


def code_box(c, x, y, w, h, lines, title_value="SQL"):
    rounded(c, x, y, w, h, NAVY, NAVY, 9)
    badge(c, title_value, x + 14, y + h - 28, BLUE, 42)
    cursor = y + h - 50
    for line in lines:
        text(c, line, x + 16, cursor, 8.5, HexColor("#DCE7FF"), "Korean")
        cursor -= 14


def cover(c):
    c.setFillColor(NAVY)
    c.rect(0, 0, W, H, fill=1, stroke=0)
    c.setFillColor(BLUE)
    c.circle(W - 100, H - 80, 180, fill=1, stroke=0)
    c.setFillColor(PURPLE)
    c.circle(W - 25, 40, 120, fill=1, stroke=0)
    badge(c, "BACKEND CASE STUDY", 58, H - 95, GREEN, 145)
    text(c, "POINT", 58, H - 180, 42, white, "KoreanBold")
    text(c, "PAYMENT LAB", 58, H - 228, 42, white, "KoreanBold")
    text(c, "Legacy 포인트 결제의 중복 요청과 외부 API 정합성을\nRedis + DB 멱등성까지 단계적으로 개선한 프로젝트",
         58, H - 285, 15, HexColor("#DCE7FF"), max_width=510, leading=24)
    rounded(c, 58, 92, 485, 105, HexColor("#223353"), HexColor("#3A4C6A"), 12)
    text(c, "JAVA 17   SPRING BOOT   JPA   MYSQL   REDIS   REDISSON", 80, 165, 10, HexColor("#AFC7FF"), "KoreanBold")
    text(c, "개인 프로젝트 · 백엔드 설계 / 구현 / 동시성 검증", 80, 133, 11, white, "KoreanBold")
    text(c, "2026 · soyunlee", 80, 107, 9, HexColor("#AFC7FF"))
    text(c, "문제 재현 → 원인 분석 → 단계별 방어선 → 실제 증거", W - 54, 32, 9, white, "KoreanBold", align="right")


def build():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(OUTPUT), pagesize=(W, H), pageCompression=1)
    c.setTitle("Point Payment Lab - Backend Portfolio")
    c.setAuthor("soyunlee")

    cover(c); c.showPage()

    page_title(c, 2, "EXECUTIVE SUMMARY", "한 번의 결제는 한 번만 실행되어야 한다", "최종 DB 행뿐 아니라 외부 API 부작용과 최초 응답까지 멱등성의 범위로 정의했다.")
    metric(c, 42, 338, 170, "외부 중복 발행", "2건", "1건")
    metric(c, 227, 338, 170, "MySQL Deadlock", "1건", "0건")
    metric(c, 412, 338, 170, "잔액 부족 외부 호출", "1건", "0건")
    metric(c, 597, 338, 200, "완료 재요청 평균", "8.118ms", "2.588ms")
    card(c, 42, 151, 235, 145, "문제", "동일 orderId 동시 요청이 외부 쿠폰을 두 장 발행하고, 내부 트랜잭션은 Deadlock과 HTTP 500을 발생시켰다.", RED, RED_LIGHT)
    card(c, 301, 151, 235, 145, "접근", "Legacy를 보존한 채 API 응답, DB 행, 외부 발행 수, 잔액과 lot를 함께 수집하고 방어선을 단계별로 추가했다.", BLUE, BLUE_LIGHT)
    card(c, 560, 151, 237, 145, "결과", "DB unique, Redisson 분산락, Redis 결과 cache, 조건부 UPDATE를 역할별로 배치하고 장애 fallback까지 검증했다.", GREEN, GREEN_LIGHT)
    c.showPage()

    page_title(c, 3, "PROJECT CONTEXT", "운영 기능이 아니라 결제 문제를 관찰하는 실험실", "기존 흐름을 재현하고 실패 조건을 통제해 개선 전후를 같은 증거로 비교했다.")
    card(c, 42, 302, 355, 155, "목표", "포인트로 외부 바우처를 구매·환불하는 Legacy 흐름을 Java/Spring Boot로 재현하고, 따닥 결제·외부 중복 호출·잔액 경쟁을 개선한다.", BLUE, BLUE_LIGHT)
    card(c, 420, 302, 377, 155, "구현 범위", "상품 관리, 포인트 결제·환불, 외부 발행사 Mock, 조회 UI, API 로그 다운로드, Flyway 스키마, 동시 요청·장애 검증 스크립트.", GREEN, GREEN_LIGHT)
    card(c, 42, 132, 355, 135, "명시적으로 제외", "실제 승인기관 연동, 운영 인증·인가, 쿠폰 Redemption, 보상 outbox, limited_deal 로직. 구현한 것과 설계만 한 것을 구분한다.", PURPLE, PURPLE_LIGHT)
    card(c, 420, 132, 377, 135, "내 역할", "문제 정의, 데이터 모델 분석, 재현 스크립트, Spring/JPA 구현, Redis/Redisson 설계, MySQL 동시성 검증, evidence와 포트폴리오 문서화.", ORANGE, ORANGE_LIGHT)
    c.showPage()

    page_title(c, 4, "SYSTEM ARCHITECTURE", "쇼핑몰과 외부 발행사는 서로 다른 정합성 경계", "MySQL rollback은 이미 호출된 외부 API를 되돌리지 못한다.")
    flow_box(c, 48, 265, 150, 95, "사용자 / 관리자", "정적 Web UI\nAPI 로그 다운로드", PURPLE, PURPLE_LIGHT)
    flow_box(c, 255, 245, 220, 135, "Shopping Mall", "Spring Boot\n결제 · 환불 · 멱등성\n8080 / 8081", BLUE, BLUE_LIGHT)
    flow_box(c, 540, 265, 150, 95, "MySQL 8.4", "도메인 상태\n최종 멱등 기록", ORANGE, ORANGE_LIGHT)
    flow_box(c, 540, 130, 150, 95, "Redis 7.4", "RLock\nRBucket + TTL", RED, RED_LIGHT)
    flow_box(c, 710, 245, 100, 135, "외부 발행사", "Mock API\nissue / cancel\n자체 unique", GREEN, GREEN_LIGHT)
    arrow(c, 198, 312, 255, 312, PURPLE, "HTTP")
    arrow(c, 475, 312, 540, 312, ORANGE, "JPA")
    arrow(c, 475, 195, 540, 177, RED, "Redisson")
    arrow(c, 690, 312, 710, 312, GREEN, "REST")
    card(c, 48, 82, 360, 95, "핵심 경계", "외부 issue 성공 후 내부 DB가 rollback되면 쿠폰은 남는다. 따라서 멱등성, 상태 기록, 보상 취소와 재시도 정책이 각각 필요하다.", RED, white)
    card(c, 428, 82, 369, 95, "역할 분리", "Redis는 조율과 빠른 replay, MySQL은 최종 기록과 잔액 원자성, provider unique는 외부 중복 발행 방어를 담당한다.", BLUE, white)
    c.showPage()

    page_title(c, 5, "DOMAIN MODEL", "잔액, 사용 단위, 원장, 구매 결과를 분리했다", "payment_attempt은 구매 자체가 아니라 요청 처리 상태와 최초 응답을 보존한다.")
    flow_box(c, 42, 330, 135, 70, "point_wallet", "사용자 포인트 지갑", BLUE, BLUE_LIGHT)
    flow_box(c, 222, 330, 145, 70, "point_balance", "현재 총잔액", ORANGE, ORANGE_LIGHT)
    flow_box(c, 412, 380, 160, 70, "point_source_balance", "출처별 잔액", ORANGE, ORANGE_LIGHT)
    flow_box(c, 412, 280, 160, 70, "point_lot", "만료일·출처 사용 단위", ORANGE, ORANGE_LIGHT)
    flow_box(c, 617, 380, 150, 70, "point_ledger", "잔액 변동 원장", GREEN, GREEN_LIGHT)
    flow_box(c, 617, 280, 150, 70, "point_credit", "환불 입금 기록", GREEN, GREEN_LIGHT)
    arrow(c, 177, 365, 222, 365)
    arrow(c, 367, 365, 412, 415, ORANGE)
    arrow(c, 367, 350, 412, 315, ORANGE)
    arrow(c, 572, 415, 617, 415, GREEN)
    arrow(c, 572, 315, 617, 315, GREEN)
    flow_box(c, 120, 125, 170, 75, "voucher_product", "판매 상품·가격·사용기간", PURPLE, PURPLE_LIGHT)
    flow_box(c, 350, 125, 170, 75, "voucher_purchase", "쇼핑몰 구매 결과", BLUE, BLUE_LIGHT)
    flow_box(c, 580, 125, 170, 75, "provider_voucher", "외부 발행사 쿠폰", GREEN, GREEN_LIGHT)
    arrow(c, 290, 162, 350, 162, PURPLE)
    arrow(c, 520, 162, 580, 162, GREEN)
    card(c, 42, 65, 250, 45, "payment_attempt", "PROCESSING / SUCCEEDED / FAILED + 최초 status/body", RED, white, 10, 8)
    card(c, 312, 65, 230, 45, "voucherNumber", "쿠폰 식별·환불·추적", BLUE, white, 10, 8)
    card(c, 562, 65, 233, 45, "pinNumber", "향후 사용 권한 검증용 비밀값", PURPLE, white, 10, 8)
    c.showPage()

    page_title(c, 6, "LEGACY FLOW", "외부 쿠폰을 먼저 발행하고 내부 포인트를 나중에 차감", "내부 트랜잭션 실패 시 외부 상태는 보상 API에 의존한다.")
    labels = [("1", "지갑·상품·잔액 조회"), ("2", "외부 issue 호출"), ("3", "쿠폰 번호·PIN 생성"), ("4", "lot/source/balance 차감"), ("5", "ledger·purchase 저장")]
    x = 42
    for idx, (n, label) in enumerate(labels):
        color, light = (GREEN, GREEN_LIGHT) if idx == 2 else ((RED, RED_LIGHT) if idx == 1 else (BLUE, BLUE_LIGHT))
        flow_box(c, x, 290, 135, 80, f"{n}. {label}", "", color, light)
        if idx < len(labels) - 1:
            arrow(c, x + 135, 330, x + 155, 330, color)
        x += 155
    card(c, 42, 120, 355, 120, "결제 실패 시", "MySQL은 rollback되지만 외부 쿠폰은 자동 취소되지 않는다. 애플리케이션이 cancel API를 호출하며, cancel마저 실패하면 불일치가 남는다.", RED, RED_LIGHT)
    card(c, 420, 120, 377, 120, "환불 흐름", "purchase·ledger·lot을 조회해 내부 포인트를 복구한 뒤 외부 cancel을 호출한다. voucherNumber가 내부와 외부 기록을 연결한다.", ORANGE, ORANGE_LIGHT)
    c.showPage()

    page_title(c, 7, "PROBLEM REPRODUCTION", "같은 orderId 두 요청이 외부 쿠폰을 각각 발행했다", "최종 구매가 한 건이어도 외부 부작용은 이미 두 번 발생했다.", "REPRODUCED")
    flow_box(c, 48, 360, 150, 70, "요청 A", "same orderId", RED, RED_LIGHT)
    flow_box(c, 48, 250, 150, 70, "요청 B", "same orderId", RED, RED_LIGHT)
    flow_box(c, 280, 305, 190, 90, "Legacy Payment API", "선점 기록 없음\n두 요청 모두 통과", BLUE, BLUE_LIGHT)
    flow_box(c, 560, 305, 180, 90, "Provider Mock", "쿠폰 2장 발행", GREEN, GREEN_LIGHT)
    arrow(c, 198, 395, 280, 365, RED)
    arrow(c, 198, 285, 280, 335, RED)
    arrow(c, 470, 350, 560, 350, GREEN, "issue × 2")
    card(c, 48, 120, 220, 100, "응답 A", "HTTP 500\nMySQL Deadlock", RED, RED_LIGHT)
    card(c, 290, 120, 220, 100, "응답 B", "HTTP 201\n결제 성공", GREEN, GREEN_LIGHT)
    card(c, 532, 120, 265, 100, "외부 결과", "ISSUED 1건 + CANCELED 1건\n보상 cancel 1회", ORANGE, ORANGE_LIGHT)
    c.showPage()

    page_title(c, 8, "ROOT CAUSE", "Deadlock은 lock 자체가 아니라 획득 순서의 순환 대기", "외부 중복 발행과 내부 Deadlock은 같은 동시 요청에서 드러난 서로 다른 문제다.", "ANALYZED")
    flow_box(c, 72, 310, 255, 115, "Transaction A", "point_source_balance lock 보유\n→ voucher_purchase unique 대기", RED, RED_LIGHT)
    flow_box(c, 510, 310, 255, 115, "Transaction B", "voucher_purchase unique 보유\n→ point_source_balance lock 대기", RED, RED_LIGHT)
    arrow(c, 327, 385, 510, 385, RED, "A waits")
    c.setStrokeColor(RED); c.setLineWidth(2); c.line(510, 345, 327, 345); c.line(327, 345, 333, 350); c.line(327, 345, 333, 340)
    text(c, "B waits", 418, 332, 8, RED, "KoreanBold", align="center")
    rounded(c, 302, 200, 235, 65, RED, RED, 12)
    text(c, "순환 대기 → MySQL이 한 트랜잭션을 중단", 419, 235, 11, white, "KoreanBold", align="center")
    card(c, 72, 82, 330, 85, "핵심 판단", "DB unique가 최종 중복을 막아도 외부 API는 이미 두 번 호출됐다. 외부 호출 전에 요청 실행권을 선점해야 한다.", BLUE, white)
    card(c, 435, 82, 330, 85, "수집한 증거", "HTTP A/B, provider_voucher, voucher_purchase, point_balance, point_lot, Deadlock 메시지를 같은 실험 단위로 저장했다.", GREEN, white)
    c.showPage()

    page_title(c, 9, "IMPROVEMENT 1", "payment_attempt로 외부 호출 전에 주문 실행권을 선점", "DB unique를 여러 서버에서도 동작하는 최종 경쟁 방어선으로 사용했다.")
    steps = [("1", "PROCESSING INSERT", BLUE), ("2", "unique 승자만 issue", GREEN), ("3", "내부 결제", ORANGE), ("4", "SUCCEEDED 결과 저장", PURPLE)]
    x = 55
    for i, (n, label, color) in enumerate(steps):
        flow_box(c, x, 320, 155, 80, f"{n}. {label}", "", color, white)
        if i < 3: arrow(c, x + 155, 360, x + 185, 360, color)
        x += 190
    table(c, 55, 280, [190, 210, 290], [
        ["요청 상태", "HTTP", "동작"],
        ["최초", "201", "외부 발행과 결제 실행"],
        ["동시 중복", "409 PROCESSING", "외부 호출 전 차단"],
        ["완료 후 재요청", "200", "저장된 바우처 결과 반환"],
    ], 28)
    metric(c, 55, 55, 155, "외부 발행", "2", "1")
    metric(c, 225, 55, 155, "Deadlock", "1", "0")
    metric(c, 395, 55, 155, "보상 취소", "1", "0")
    card(c, 565, 55, 230, 77, "남은 경계", "외부 Mock API를 직접 호출하면 쇼핑몰 payment_attempt를 우회할 수 있다.", RED, RED_LIGHT, 10, 8)
    c.showPage()

    page_title(c, 10, "IMPROVEMENT 2", "외부 발행사도 자신의 orderId 멱등성을 보장", "쇼핑몰과 발행사 양쪽이 각자의 데이터 정합성을 스스로 방어한다.")
    flow_box(c, 55, 300, 180, 95, "최초 issue", "provider_voucher INSERT\nHTTP 201", GREEN, GREEN_LIGHT)
    flow_box(c, 330, 300, 180, 95, "같은 주문·상품", "기존 voucher/PIN replay\nHTTP 200", BLUE, BLUE_LIGHT)
    flow_box(c, 605, 300, 180, 95, "같은 주문·다른 상품", "IDEMPOTENCY_KEY_REUSED\nHTTP 409", RED, RED_LIGHT)
    code_box(c, 55, 155, 350, 105, ["ALTER TABLE provider_voucher", "ADD CONSTRAINT uk_provider_order", "UNIQUE (order_id);"], "DDL")
    card(c, 435, 155, 350, 105, "실제 검증", "동시 issue 두 건의 voucherNumber와 pinNumber가 동일했다. 최초 HTTP 201, replay HTTP 200, DB 행은 1건이었다.", GREEN, white)
    card(c, 55, 75, 730, 50, "PIN의 역할", "voucherNumber는 쿠폰 식별자이고 PIN은 향후 Redemption에서 사용 권한을 확인하는 비밀값이다. 현재는 발급·저장·조회까지만 구현했다.", PURPLE, PURPLE_LIGHT, 10, 8)
    c.showPage()

    page_title(c, 11, "IMPROVEMENT 3", "실패가 확실한 요청은 외부 API 전에 차단", "가격, 총잔액, 실제 사용 가능한 lot 합계를 모두 검증한다.")
    flow_box(c, 55, 320, 175, 85, "판매가 검증", "requestedPoint = sellPrice", BLUE, BLUE_LIGHT)
    flow_box(c, 330, 320, 175, 85, "총잔액 검증", "point_balance ≥ amount", ORANGE, ORANGE_LIGHT)
    flow_box(c, 605, 320, 175, 85, "lot 검증", "미만료·미사용 합계 ≥ amount", GREEN, GREEN_LIGHT)
    arrow(c, 230, 362, 330, 362)
    arrow(c, 505, 362, 605, 362)
    metric(c, 55, 185, 220, "잔액 부족 HTTP", "500", "422")
    metric(c, 300, 185, 220, "외부 issue", "1", "0")
    metric(c, 545, 185, 235, "외부 cancel", "1", "0")
    card(c, 55, 78, 725, 75, "남은 TOCTOU", "사전 조회와 실제 차감 사이에 다른 주문이 잔액을 먼저 사용할 수 있다. 빠른 오류를 위한 검증과 동시성 정확성을 위한 조건부 UPDATE는 서로 대체하지 않는다.", RED, RED_LIGHT)
    c.showPage()

    page_title(c, 12, "IMPROVEMENT 4", "Redis는 DB 대체재가 아니라 분산 조율과 결과 재사용 계층", "같은 Idempotency-Key가 8080과 8081에 동시에 도착해도 한 요청만 실행된다.")
    flow_box(c, 42, 325, 120, 80, "Client", "same key", PURPLE, PURPLE_LIGHT)
    flow_box(c, 210, 370, 135, 65, "API A :8080", "", BLUE, BLUE_LIGHT)
    flow_box(c, 210, 275, 135, 65, "API B :8081", "", BLUE, BLUE_LIGHT)
    flow_box(c, 405, 320, 150, 90, "Redis/Redisson", "RBucket cache\nRLock + watchdog", RED, RED_LIGHT)
    flow_box(c, 615, 320, 150, 90, "MySQL", "payment_attempt\nstatus + body", ORANGE, ORANGE_LIGHT)
    arrow(c, 162, 375, 210, 402, PURPLE)
    arrow(c, 162, 355, 210, 307, PURPLE)
    arrow(c, 345, 402, 405, 375, RED)
    arrow(c, 345, 307, 405, 355, RED)
    arrow(c, 555, 365, 615, 365, ORANGE)
    table(c, 42, 245, [185, 245, 305], [
        ["장치", "담당 문제", "최종 역할"],
        ["Redisson RLock", "같은 키 동시 실행", "한 서버만 결제 로직 진입"],
        ["Redis RBucket", "완료 재요청", "TTL 1시간 최초 응답 cache"],
        ["payment_attempt unique", "Redis 장애·TTL 만료", "최종 중복 방지와 결과 복구"],
    ], 32)
    card(c, 42, 62, 735, 50, "정확한 replay", "최초 HTTP 201과 JSON body를 MySQL과 Redis에 저장해 완료 재요청도 status/body를 그대로 반환한다.", GREEN, GREEN_LIGHT, 10, 8)
    c.showPage()

    page_title(c, 13, "FAILURE RECOVERY", "cache가 사라져도 최초 결과는 MySQL에서 복구", "성능 계층이 실패해도 정확성 계층은 유지되어야 한다.")
    table(c, 50, 430, [210, 260, 300], [
        ["상황", "응답 출처", "검증 결과"],
        ["Redis cache hit", "REDIS_CACHE", "HTTP 201 / 동일 body"],
        ["cache key 삭제", "DATABASE_CACHE_REBUILD", "DB replay 후 cache 재생성"],
        ["Redis container 중지", "DATABASE_REPLAY", "HTTP 201 / 중복 실행 없음"],
        ["같은 키·다른 payload", "request SHA-256 비교", "HTTP 422 IDEMPOTENCY_KEY_REUSED"],
    ], 38)
    card(c, 50, 145, 360, 95, "2개 인스턴스 동시 요청", "A: HTTP 201 / DATABASE_CREATED\nB: HTTP 201 / REDIS_CACHE\n두 응답의 voucher, PIN, body 완전 동일", GREEN, GREEN_LIGHT)
    card(c, 435, 145, 360, 95, "최종 DB", "payment_attempt 1건\nprovider_voucher 1건\nvoucher_purchase 1건\n포인트 차감 1회", BLUE, BLUE_LIGHT)
    card(c, 50, 72, 745, 45, "운영 주의", "PIN이 cache에 포함되므로 private network, 인증, TLS, 접근 통제, 로그 마스킹과 짧은 보관 정책이 필요하다.", RED, RED_LIGHT, 10, 8)
    c.showPage()

    page_title(c, 14, "BALANCE CONCURRENCY", "서로 다른 주문의 같은 잔액 경쟁은 조건부 UPDATE로 방어", "Idempotency-Key가 다르면 Redis lock도 다르므로 DB 자원 자체의 원자성이 필요하다.")
    code_box(c, 45, 285, 390, 155, [
        "UPDATE point_balance",
        "SET balance = CAST(balance AS SIGNED) - :amount",
        "WHERE id = :pointBalanceId",
        "  AND point_wallet_id = :pointWalletId",
        "  AND CAST(balance AS SIGNED) >= :amount;",
        "-- affected rows = 1 only",
    ], "ATOMIC SQL")
    flow_box(c, 495, 355, 130, 80, "주문 A", "different key", BLUE, BLUE_LIGHT)
    flow_box(c, 495, 250, 130, 80, "주문 B", "different key", PURPLE, PURPLE_LIGHT)
    flow_box(c, 680, 300, 130, 90, "point_balance", "row X lock\n조건 재평가", ORANGE, ORANGE_LIGHT)
    arrow(c, 625, 395, 680, 365, BLUE)
    arrow(c, 625, 290, 680, 325, PURPLE)
    card(c, 45, 125, 235, 100, "요청 A", "HTTP 409\nPOINT_BALANCE_CONFLICT\npayment_attempt FAILED", RED, RED_LIGHT)
    card(c, 302, 125, 235, 100, "요청 B", "HTTP 201\n결제 성공\npayment_attempt SUCCEEDED", GREEN, GREEN_LIGHT)
    card(c, 559, 125, 251, 100, "최종 상태", "잔액 0\n구매 1건\n포인트 중복 사용 0", BLUE, BLUE_LIGHT)
    card(c, 45, 64, 765, 40, "남은 비용", "경쟁 실패 요청은 이미 외부 쿠폰을 발행해 CANCELED 1건이 남았다. 외부 부작용 제거에는 포인트 예약 상태가 필요하다.", ORANGE, ORANGE_LIGHT, 9.5, 7.7)
    c.showPage()

    page_title(c, 15, "MEASURED RESULTS", "완료 재요청 100회에서 cache hit의 DB 접근과 지연을 줄였다", "로컬 단일 인스턴스·순차 호출·SQL 로그 활성화 환경의 방향성 비교다.")
    table(c, 65, 415, [180, 150, 120, 120, 140], [
        ["방식", "평균 응답", "MySQL SELECT", "INSERT 시도", "HTTP"],
        ["DB-only", "8.118ms", "101", "100", "200 replay"],
        ["Redis+DB cache hit", "2.588ms", "1*", "0", "최초 201 replay"],
    ], 40)
    rounded(c, 80, 175, 700, 105, white, GRAY_300, 12)
    text(c, "평균 응답 시간", 105, 255, 10, GRAY_500, "KoreanBold")
    c.setFillColor(RED); c.roundRect(105, 220, 510, 24, 7, fill=1, stroke=0)
    text(c, "DB-only  8.118ms", 120, 227, 8.5, white, "KoreanBold")
    c.setFillColor(GREEN); c.roundRect(105, 187, 163, 24, 7, fill=1, stroke=0)
    text(c, "Redis+DB  2.588ms", 120, 194, 8.5, white, "KoreanBold")
    badge(c, "약 68.1% 감소", 640, 202, GREEN, 115)
    card(c, 65, 75, 715, 85, "해석", "DB-only는 재요청마다 payment_attempt INSERT 선점과 unique 충돌 후 SELECT를 수행했다. Redis cache hit는 결제 DB에 접근하지 않았다. *SELECT 1회는 global counter 환경 잡음을 포함할 수 있다.", BLUE, BLUE_LIGHT)
    c.showPage()

    page_title(c, 16, "ENGINEERING DECISIONS", "하나의 기술이 아니라 문제별 방어선을 조합", "동시성, 멱등성, 외부 정합성, 성능은 겹치지만 동일한 문제가 아니다.")
    decisions = [
        ("DB unique", "Redis보다 먼저 도입", "정확성의 최종 근거이며 재시작과 다중 서버에서도 유지", ORANGE),
        ("Redisson", "직접 SET NX보다 선택", "RLock 소유권·원자적 unlock·watchdog을 검증된 구현으로 사용", RED),
        ("사전 검증", "외부 호출 전에 수행", "명백한 실패의 발행·취소 비용을 제거하되 동시성 보장으로 과장하지 않음", BLUE),
        ("조건부 UPDATE", "지갑 Redis lock 대신 선택", "잔액 자원 자체를 MySQL에서 원자적으로 선점하고 affected rows로 판정", GREEN),
    ]
    y = 370
    for title_value, choice, reason, color in decisions:
        rounded(c, 50, y, 745, 78, white, GRAY_300, 10)
        badge(c, title_value, 65, y + 43, color, 115)
        text(c, choice, 205, y + 50, 10, NAVY, "KoreanBold")
        text(c, reason, 205, y + 27, 8.8, GRAY_700, max_width=565)
        y -= 90
    c.showPage()

    page_title(c, 17, "LIMITATIONS & ROADMAP", "구현 완료와 다음 과제를 명확히 구분", "현재 결과를 과장하지 않고 운영 환경에서 필요한 상태와 복구 흐름을 남겼다.", "NEXT")
    card(c, 45, 315, 235, 120, "보상 취소 Outbox", "외부 cancel 실패를 compensation_task에 기록하고 지수 backoff 재시도·운영 알람·reconciliation을 추가한다.", RED, RED_LIGHT)
    card(c, 302, 315, 235, 120, "포인트 예약", "외부 issue 전에 짧은 DB 트랜잭션으로 잔액을 RESERVED 처리하고 성공·실패에 따라 확정 또는 해제한다.", ORANGE, ORANGE_LIGHT)
    card(c, 559, 315, 235, 120, "Redemption", "voucherNumber + pinNumber를 검증하고 UNUSED→USED를 원자적으로 전이해 실제 쿠폰 사용 흐름을 완성한다.", PURPLE, PURPLE_LIGHT)
    card(c, 45, 160, 235, 120, "금액 타입 Migration", "legacy varchar balance를 BIGINT 또는 DECIMAL로 변경해 CAST 제거, 타입 안정성, 인덱스 활용을 확보한다.", BLUE, BLUE_LIGHT)
    card(c, 302, 160, 235, 120, "Redis 운영 구성", "인증·TLS·private network·Sentinel/Cluster, 장애 지표와 cache hit/miss 관측성을 추가한다.", RED, RED_LIGHT)
    card(c, 559, 160, 235, 120, "통합 테스트와 CI", "Testcontainers 기반 MySQL/Redis 동시성 테스트를 자동화하고 반복 부하·장애 주입 결과를 회귀 검증한다.", GREEN, GREEN_LIGHT)
    c.showPage()

    page_title(c, 18, "RETROSPECTIVE", "최종 상태만 보지 않고 시스템 경계의 부작용까지 추적했다", "재현 가능한 evidence와 정직한 한계가 설계 선택의 신뢰도를 만든다.")
    card(c, 45, 320, 235, 120, "배운 점 01", "내부 구매가 1건이어도 외부 발행이 2건이면 결제 시스템은 안전하지 않다. API 경계를 포함해 성공을 정의해야 한다.", RED, RED_LIGHT)
    card(c, 302, 320, 235, 120, "배운 점 02", "멱등성은 중복 요청을 무시하는 것이 아니라 요청 동일성을 검증하고 최초 status/body를 안전하게 재사용하는 것이다.", BLUE, BLUE_LIGHT)
    card(c, 559, 320, 235, 120, "배운 점 03", "Redis lock, DB unique, 조건부 UPDATE, 보상 처리는 서로 대체하지 않는다. 각 실패 모드에 맞는 방어선이 필요하다.", GREEN, GREEN_LIGHT)
    table(c, 45, 260, [250, 505], [
        ["재현 가능한 자료", "위치"],
        ["Legacy 동시 결제", "evidence/duplicate-payment-legacy"],
        ["외부 발행 멱등성", "evidence/provider-issue-idempotency"],
        ["Redis 2개 인스턴스·장애·성능", "evidence/redis-idempotency"],
        ["조건부 잔액 차감", "evidence/conditional-balance-debit"],
    ], 31)
    rounded(c, 45, 63, 755, 52, NAVY, NAVY, 10)
    text(c, "문제 재현 → 원인 분석 → 단계별 개선 → 실제 검증 → 남은 한계", W / 2, 82, 13, white, "KoreanBold", align="center")

    c.save()
    print(OUTPUT)


if __name__ == "__main__":
    build()
