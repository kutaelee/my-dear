import { useState, type ComponentType } from "react";
import {
  ChatBubbleIcon, ChevronRightIcon, ClockIcon, ExternalLinkIcon, GearIcon,
  GlobeIcon, LockClosedIcon, MobileIcon, PaperPlaneIcon,
  RowsIcon, SunIcon,
} from "@radix-ui/react-icons";
import { MdMic } from "react-icons/md";
import { KeyboardTextarea, MobileScroll, useKeyboardInsets } from "./mobile";

type Tab = "chat" | "history" | "settings";

function ChatScreen() {
  const [tutorialStep, setTutorialStep] = useState(1);
  const [listening, setListening] = useState(false);
  const tutorialCopy = [
    "이 버튼을 누르고 편하게 말씀하세요",
    "다 말씀하셨으면 ‘다 말했어요’를 누르세요",
    "웹 검색 때는 검색어만 인터넷으로 보내요",
    "설정에서 글자와 목소리 속도를 바꿀 수 있어요",
  ];
  return <>
    <section className="welcome-card" aria-label="인사말"><img className="buddy-face" src="/my-dear-mascot.png" alt="" /><h1>오늘은 무엇을<br />도와드릴까요?</h1></section>
    <section className="quick-actions" aria-label="빠른 질문">
      <button><span className="quick-icon sun"><SunIcon /></span><strong>오늘 날씨 알려줘</strong><ChevronRightIcon aria-hidden="true" /></button>
      <button><span className="quick-icon call"><MobileIcon /></span><strong>가족에게 전화하기</strong><ChevronRightIcon aria-hidden="true" /></button>
      <button><span className="quick-icon clock"><ClockIcon /></span><strong>약 먹을 시간 기억해줘</strong><ChevronRightIcon aria-hidden="true" /></button>
    </section>
    <div className="message user-message">오늘 서울 날씨 어때?</div>
    <article className="answer-card">
      <div className="assistant-row"><img className="mini-buddy" src="/my-dear-mascot.png" alt="" /><strong>오늘 서울은 구름이 많고 낮 기온은 28℃로 덥겠습니다. 오후에 소나기가 내릴 수 있으니 우산을 챙기세요.</strong></div>
      <div className="web-label"><GlobeIcon aria-hidden="true" /> 웹에서 찾았어요 · 오후 2:10</div>
      <button className="source-row"><span className="weather-mark" aria-hidden="true"><SunIcon /></span><span><strong>기상청</strong><small>2026년 8월 13일 업데이트</small></span><ExternalLinkIcon aria-hidden="true" /></button>
    </article>
    {tutorialStep <= 4 && <aside className="coachmark" aria-live="polite" aria-label={`사용법 ${tutorialStep}단계, 총 4단계`}>
      <div className="coachmark-title"><span>{tutorialStep}단계</span><strong>{tutorialCopy[tutorialStep - 1]}</strong><em>{tutorialStep} / 4</em></div>
      <div className="coachmark-actions"><button onClick={() => setTutorialStep(5)}>건너뛰기</button>{tutorialStep > 1 && <button onClick={() => setTutorialStep((step) => step - 1)}>이전</button>}<button className="next" onClick={() => setTutorialStep((step) => step + 1)}>{tutorialStep === 4 ? "시작하기" : "다음"}</button></div>
    </aside>}
    <button className={`voice-button ${listening ? "is-listening" : ""}`} aria-label={listening ? "다 말했어요" : "말로 질문하기"} onClick={() => setListening((value) => !value)}><MdMic aria-hidden="true" /><strong>{listening ? "듣고 있어요" : "말로 하기"}</strong><small>{listening ? "다 말했어요" : "눌러서 시작"}</small></button>
    <div className="privacy-line"><LockClosedIcon aria-hidden="true" /> 대화는 이 휴대폰에서 처리돼요</div>
  </>;
}

function HistoryScreen() {
  return <section className="secondary-screen"><h1>채팅 목록</h1><p>지난 대화를 다시 볼 수 있어요.</p><button className="history-card"><strong>오늘 서울 날씨</strong><span>오늘 · 오후 2:10</span><small>오후에 소나기가 내릴 수 있어요…</small></button><button className="history-card"><strong>약 먹을 시간</strong><span>어제 · 오전 9:30</span><small>매일 아침 9시에 알려드릴게요.</small></button></section>;
}

function SettingsScreen() {
  const [showPlans, setShowPlans] = useState(false);
  return <section className="secondary-screen"><h1>설정</h1><p>나에게 편한 모습과 목소리로 바꿔보세요.</p>
    <article className="plan-card"><div><span className="plan-badge">무료 사용 중</span><strong>휴대폰 안에서 기본 대화는 계속 무료예요</strong><small>더 많은 검색과 고급 AI는 플러스에서 이용할 수 있어요.</small></div><button onClick={() => setShowPlans((value) => !value)} aria-expanded={showPlans}>{showPlans ? "요금제 닫기" : "요금제 보기"}</button></article>
    {showPlans && <section className="plan-compare" aria-label="요금제 비교"><div><strong>무료</strong><em>0원</em><span>오프라인 채팅 · 음성</span><span>기본 웹 검색</span></div><div className="plus-plan"><strong>내새끼 플러스</strong><em>월 3,900원 예정</em><span>더 많은 웹 검색</span><span>온라인 고급 AI 답변</span><span>가족 기능은 추후 제공</span><button>출시 알림 받기</button></div><p>실제 결제는 아직 연결되지 않았어요. 언제든 해지할 수 있고, 온라인 기능을 쓸 때만 필요한 질문이 서버로 전송돼요.</p></section>}
    {[["글자 크기","크게"],["목소리 속도","천천히"],["사용법 다시 보기","4단계 안내"],["개인정보와 인터넷 검색","휴대폰 안에서 우선 처리"]].map(([title, detail]) => <button className="setting-row" key={title}><span><strong>{title}</strong><small>{detail}</small></span><ChevronRightIcon aria-hidden="true" /></button>)}
  </section>;
}

export default function Prototype() {
  const [tab, setTab] = useState<Tab>("chat");
  const { bottomInset } = useKeyboardInsets();
  const tabs: Array<[Tab, ComponentType<{"aria-hidden"?: "true"}>, string]> = [["chat", ChatBubbleIcon, "채팅"], ["history", RowsIcon, "채팅 목록"], ["settings", GearIcon, "설정"]];
  return <main className="my-dear-shell" style={{ "--keyboard-inset": `${bottomInset}px` } as React.CSSProperties}>
    <header className="app-header"><div className="brand"><img className="heart-logo" src="/my-dear-mascot.png" alt="" /><strong>내새끼</strong></div><button className="text-size" aria-label="글자 크기 설정"><span>AA</span> 글자 크게</button></header>
    <MobileScroll className="app-screen"><div className={`screen-content ${tab === "chat" ? "chat-content" : ""}`}>{tab === "chat" ? <ChatScreen /> : tab === "history" ? <HistoryScreen /> : <SettingsScreen />}</div></MobileScroll>
    {tab === "chat" && <div className="composer-wrap"><KeyboardTextarea aria-label="메시지 입력" placeholder="메시지를 입력하세요" /><button className="send-button" aria-label="메시지 보내기"><PaperPlaneIcon aria-hidden="true" /><span>보내기</span></button></div>}
    <nav className="bottom-nav" aria-label="주요 메뉴">{tabs.map(([value, Icon, label]) => <button key={value} className={tab === value ? "selected" : ""} aria-current={tab === value ? "page" : undefined} onClick={() => setTab(value)}><Icon aria-hidden="true" /><span>{label}</span></button>)}</nav>
  </main>;
}
