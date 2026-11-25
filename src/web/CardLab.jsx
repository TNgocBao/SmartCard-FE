import React, {useEffect, useRef, useState} from 'react';
import {api, endpoint} from './api.js';

export const cardTransports = [
  ['SOFTWARE','Thẻ phần mềm · demo trên UI'],
  ['SIMULATOR','Oracle Java Card Simulator · APDU'],
  ['PCSC','Thẻ thật · NFC Reader / PC/SC'],
];
export const registrationTransport = mode => mode;

export default function CardLab({transport, setTransport, run, busy, signedIn, canManage, onChanged, show, pinField, children}) {
  const [agent,setAgent]=useState(null),[reader,setReader]=useState(null),[result,setResult]=useState('');
  const [account,setAccount]=useState(''),[accounts,setAccounts]=useState([]),[cards,setCards]=useState([]),[history,setHistory]=useState([]),[page,setPage]=useState(0);
  const proof=useRef(null);
  async function refreshReader(){const a=await api.call('GET','/bridge/status');setAgent(a);if(a.demoEnabled)setReader(await api.call('GET','/bridge/demo/status'));}
  useEffect(()=>{refreshReader().catch(()=>setAgent(null));},[]);
  useEffect(()=>{proof.current=null;setResult('');},[transport]);
  useEffect(()=>{if(!signedIn||!canManage)return;let current=true;(async()=>{const list=[];let p=0;while(true){const r=await api.call('GET',`/api/v1/management/resources/account?page=${p++}&size=100`);list.push(...r.data);if(r.data.length<100)break;}if(current)setAccounts(list);})().catch(()=>{});return()=>{current=false;};},[signedIn,canManage]);
  async function refreshDb(){
    const query=`?page=${page}${account?'&accountId='+endpoint(account):''}`;
    const [c,h]=await Promise.all([api.call('GET','/api/v1/me/smart-cards'+query),api.call('GET','/api/v1/me/smart-cards/history'+query)]);
    setCards(c.data);setHistory(h.data);
  }
  useEffect(()=>{setCards([]);setHistory([]);if(signedIn)run(refreshDb);},[signedIn,account,page]);
  async function readerAction(action,body={}){await api.call('POST','/bridge/demo/'+action,body);onChanged();await refreshReader();}
  function checkPin(){show({title:'Kiểm tra PIN bằng APDU',fields:[pinField('pin','PIN thẻ')],submit:async v=>{try{const identity=await api.call('POST','/bridge/card/read',{transport});await api.call('POST','/bridge/card/verify-pin',{transport,cardIdentifier:identity.identifier,pin:v.pin});setResult('PIN hợp lệ.');}finally{await refreshReader();}}});}
  function prepareProof(){show({title:'Thử Challenge–Response',description:'Dùng tên tài khoản đã được gán thẻ. Chữ ký được tạo bởi thẻ; PIN chỉ gửi tới agent cục bộ.',fields:[{name:'username',label:'Tên đăng nhập'},pinField('pin','PIN thẻ')],submit:async v=>{
    proof.current=null;setResult('');
    try{
      const identity=await api.call('POST','/bridge/card/read',{transport});
      const challenge=await api.call('POST','/api/v1/auth/smart-card/challenge',{username:v.username.trim(),cardIdentifier:identity.identifier});
      const signed=await api.call('POST','/bridge/card/sign',{transport,cardIdentifier:identity.identifier,payload:challenge.payload,pin:v.pin});
      proof.current={challengeId:challenge.challengeId,signature:signed.signature};
      setResult(`Đã ký challenge. Hết hạn: ${new Date(challenge.expiresAt).toLocaleTimeString('vi-VN')}. Có thể xác minh ngay, thử chữ ký sai, hoặc chờ hết hạn.`);
    }finally{await refreshReader();}
  }});}
  async function verifyProof(tamper=false){
    if(!proof.current)return;
    const payload={...proof.current};
    if(tamper){const bytes=Uint8Array.from(atob(payload.signature),c=>c.charCodeAt(0));bytes[0]^=1;payload.signature=btoa(String.fromCharCode(...bytes));}
    try{
      const response=await api.call('POST','/api/v1/auth/smart-card/verify',payload);
      // This panel proves card ownership; a test session must not replace the operator's session.
      if(response.accessToken)await api.send('POST','/api/v1/logout',{},response.accessToken);
      setResult(response.requiresMfa||response.requiresEnrollment?'Chữ ký hợp lệ. Máy chủ yêu cầu bước MFA/thiết lập tiếp theo; đăng nhập đầy đủ ở màn hình đăng nhập.':'Chữ ký hợp lệ. Máy chủ đã cấp token; phiên thử được đăng xuất ngay. Bấm xác minh lần nữa để thử chống phát lại.');
    }catch(e){setResult(`Máy chủ từ chối: ${e.code}. ${e.code==='CHALLENGE_EXPIRED'?'Challenge đã dùng hoặc đã hết hạn.':''}`);throw e;}
    finally{if(signedIn)await refreshDb();}
  }
  return <section className="card-lab">
    <div className="panel"><span className="eyebrow">PHÒNG DEMO THẺ</span><h2>Nạp thẻ → đăng ký database → xác thực</h2>
      <label className="field"><span>Chế độ giao tiếp</span><select value={transport} disabled={busy} onChange={e=>{setTransport(e.target.value);onChanged();}}>{cardTransports.map(([v,t])=><option key={v} value={v}>{t}</option>)}</select></label>
      <div className="button-row"><button className="btn" disabled={busy} onClick={()=>run(refreshReader)}>Kiểm tra agent</button></div>
      {!agent&&<p role="status">Chưa kết nối agent. Chạy dịch vụ web cục bộ rồi kiểm tra lại.</p>}
      {agent&&!agent.demoEnabled&&transport!=='PCSC'&&<p>Chạy frontend/run.ps1 với tham số -DemoCard để bật công cụ demo và nạp applet.</p>}
      {transport==='SOFTWARE'&&agent?.demoEnabled&&<>
        <p>Thẻ phần mềm mô phỏng APDU và RSA trong Java. Không cần Oracle SDK. Thẻ và khóa chỉ tồn tại đến khi agent dừng; bản đăng ký trong database vẫn được giữ.</p>
        <div className="button-row"><button className="btn primary" disabled={busy} onClick={()=>run(()=>readerAction('create'))}>Tạo thẻ trắng mới</button><button className="btn" disabled={busy||!reader?.inserted} onClick={()=>run(()=>readerAction('install'))}>Khởi tạo applet mô phỏng</button><button className="btn" disabled={busy||!reader?.inserted} onClick={()=>run(()=>readerAction('remove'))}>Rút thẻ</button></div>
        <div className="table-scroll"><table><thead><tr><th>Thẻ trong agent</th><th>Applet / PIN</th><th>Đầu đọc</th></tr></thead><tbody>{reader?.cards.map(c=><tr key={c.identifier}><td><code>{c.identifier}</code></td><td>{c.installed?`Đã khởi tạo · còn ${c.remainingTries}/5 lần PIN`:'Thẻ trắng'}</td><td>{c.inserted?'Đang đặt trong đầu đọc':<button className="btn" disabled={busy} onClick={()=>run(()=>readerAction('insert',{cardIdentifier:c.identifier}))}>Đưa thẻ vào</button>}</td></tr>)}</tbody></table></div>
        <p className="muted">PIN ban đầu: 123456. Nạp lại không đổi khóa hoặc mở khóa PIN. Khi khóa PIN, tạo thẻ mới và đăng ký lại.</p>
      </>}
      {transport==='SIMULATOR'&&<><p>Nạp file CAP của IdentityApplet vào Oracle Simulator đang chạy tại 127.0.0.1:9025. Nếu applet đã có, giữ nguyên khóa và PIN.</p><p>CAP: {agent?.oracle?.capReady?'Sẵn sàng':'Chưa build'} · Installer: {agent?.oracle?.installerReady?'Sẵn sàng':'Chưa build'} · Oracle SDK: {agent?.oracle?.sdkReady?'Sẵn sàng':'Thiếu file'}</p><button className="btn primary" disabled={busy||!agent?.demoEnabled} onClick={()=>run(async()=>{await api.call('POST','/bridge/oracle/install',{});onChanged();setResult('Đã nạp/kiểm tra applet trên Oracle Simulator. Tiếp tục đọc và gán thẻ.');})}>Nạp applet CAP vào Oracle Simulator</button></>}
      {transport==='PCSC'&&<p>Kết nối thẻ thật qua PC/SC được giữ nguyên. Dùng đầu đọc hỗ trợ NFC và thẻ Java Card tương thích đã cài applet.</p>}
    </div>
    {children}
    <div className="panel"><h3>Kiểm tra PIN và Challenge–Response</h3><div className="button-row"><button className="btn" disabled={busy} onClick={checkPin}>Kiểm tra PIN / thử khóa</button><button className="btn" disabled={busy} onClick={prepareProof}>1. Lấy challenge & ký</button><button className="btn" disabled={busy||!proof.current} onClick={()=>run(()=>verifyProof(true))}>2. Thử chữ ký sai</button><button className="btn primary" disabled={busy||!proof.current} onClick={()=>run(()=>verifyProof())}>3. Xác minh / thử phát lại</button></div>{result&&<p className="callout" role="status">{result}</p>}<p className="muted">Kiểm tra PIN sai diễn ra trên thẻ/agent. Lịch sử backend ghi các lần xác minh chữ ký; không nhận hoặc lưu PIN.</p></div>
    {signedIn&&<div className="panel"><h3>Thẻ đã lưu và lịch sử trên database</h3>{canManage&&<label className="field"><span>Tài khoản cần xem</span><select value={account} disabled={busy} onChange={e=>{setAccount(e.target.value);setPage(0);}}><option value="">Tài khoản của tôi</option>{accounts.map(a=><option key={a.id} value={a.id}>{a.username}</option>)}</select></label>}<div className="button-row"><button className="btn" disabled={busy} onClick={()=>run(refreshDb)}>Tải từ database</button><button className="btn" disabled={busy||page===0} onClick={()=>setPage(page-1)}>Trước</button><span>Trang {page+1}</span><button className="btn" disabled={busy||(cards.length<50&&history.length<50)} onClick={()=>setPage(page+1)}>Sau</button></div>
      <div className="table-scroll"><table><thead><tr><th>Định danh thẻ</th><th>Kết nối</th><th>Trạng thái</th><th>Đăng ký</th><th>Thao tác</th></tr></thead><tbody>{cards.map(c=><tr key={c.id}><td><code>{c.card_identifier}</code></td><td>{c.transport}</td><td>{c.status}</td><td>{c.registered_at}</td><td><button className="btn text-danger" disabled={busy||c.status!=='ACTIVE'} onClick={()=>show({title:'Thu hồi thẻ',description:'Máy chủ sẽ vô hiệu hóa thẻ và thu hồi các phiên của tài khoản. Nếu là thẻ của bạn, có thể cần đăng nhập lại.',danger:true,submit:async()=>{await api.call('DELETE',canManage?`/api/v1/accounts/credentials/${endpoint(c.id)}`:`/api/v1/me/credentials/${endpoint(c.id)}`,{});await refreshDb();}})}>Thu hồi</button></td></tr>)}</tbody></table>{!cards.length&&<p>Chưa có thẻ trên trang này. Đọc thẻ và gán tài khoản ở phần bên dưới.</p>}</div>
      <h3>Lịch sử xác thực thẻ</h3><div className="table-scroll"><table><thead><tr><th>Thời gian</th><th>Sự kiện</th><th>Kết quả</th><th>Lý do</th></tr></thead><tbody>{history.map(e=><tr key={e.id}><td>{e.created_at}</td><td>{e.event_type}</td><td>{e.result}</td><td>{e.failure_reason_code||'—'}</td></tr>)}</tbody></table>{!history.length&&<p>Chưa có sự kiện trên trang này.</p>}</div>
    </div>}
    {transport==='SOFTWARE'&&reader?.trace.length>0&&<details className="panel"><summary>Nhật ký APDU mô phỏng · {reader.trace.length} lệnh gần nhất</summary><p>Chỉ hiển thị header, số byte và status word; không hiển thị PIN, chữ ký hoặc khóa bí mật.</p><div className="table-scroll"><table><thead><tr><th>Thời gian</th><th>CLA INS P1 P2</th><th>Gửi / nhận (byte)</th><th>SW</th></tr></thead><tbody>{reader.trace.map((t,i)=><tr key={i}><td>{new Date(t.time).toLocaleTimeString('vi-VN')}</td><td><code>{t.command}</code></td><td>{t.inputBytes} / {t.outputBytes}</td><td><code>{t.sw}</code></td></tr>)}</tbody></table></div></details>}
  </section>;
}
