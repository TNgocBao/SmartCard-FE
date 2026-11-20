import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiClient } from '../src/web/api.js';
const response = (status, data) => ({ok:status<300,status,text:async()=>JSON.stringify(data)});
test('coalesces simultaneous refreshes and retries with rotated access token', async()=>{
  let refreshes=0;
  const client=new ApiClient(async(path,options)=>{
    if(path.endsWith('/refresh')){refreshes++;await new Promise(r=>setTimeout(r,10));assert.equal(options.headers.Authorization,undefined);return response(200,{accessToken:'new',refreshToken:'newRefresh'});}
    return options.headers.Authorization==='Bearer new'?response(200,{data:[]}):response(401,{code:'INVALID_OR_REVOKED_SESSION'});
  });
  client.session({accessToken:'old',refreshToken:'refresh'});
  await Promise.all([client.call('GET','/api/v1/me'),client.call('GET','/api/v1/me/sessions')]);
  assert.equal(refreshes,1);
});
test('discards a response received after logout',async()=>{
  let finish;
  const client=new ApiClient(()=>new Promise(r=>finish=r));
  client.session({accessToken:'old',refreshToken:'refresh'});
  const pending=client.call('GET','/api/v1/me');client.clear();finish(response(200,{data:{username:'old'}}));
  await assert.rejects(pending,{code:'SESSION_CHANGED'});
});
test('does not retry a rejected write or send access tokens to card bridge',async()=>{
  let calls=0;
  const client=new ApiClient(async(path,options)=>{calls++;assert.equal(options.headers.Authorization,undefined);return response(400,{code:'PIN_INVALID',remainingTries:3});});
  client.session({accessToken:'secret',refreshToken:'refresh'});
  await assert.rejects(client.call('POST','/bridge/card/sign',{pin:'000000'}),{code:'PIN_INVALID',remainingTries:3});assert.equal(calls,1);
});
test('uncertain refresh response clears the session rather than reusing a token',async()=>{
  let expired=false;
  const client=new ApiClient(async(path)=>{if(path.endsWith('/refresh'))throw new Error('network');return response(401,{code:'INVALID_OR_REVOKED_SESSION'});});
  client.onExpired=()=>expired=true;client.session({accessToken:'old',refreshToken:'refresh'});
  await assert.rejects(client.call('GET','/api/v1/me'));assert.equal(client.tokens,null);assert.equal(expired,true);
});
test('authorization failures do not refresh or retry writes',async()=>{
  let calls=0;const client=new ApiClient(async()=>{calls++;return response(403,{code:'ACCESS_DENIED'});});
  client.session({accessToken:'old',refreshToken:'refresh'});
  await assert.rejects(client.call('POST','/api/v1/accounts',{}),{code:'ACCESS_DENIED'});assert.equal(calls,1);
});
test('malformed token rotation response expires the session',async()=>{
  const client=new ApiClient(async path=>path.endsWith('/refresh')?response(200,{accessToken:'invalid\r\nheader',refreshToken:'refresh'}):response(401,{code:'INVALID_OR_REVOKED_SESSION'}));
  client.session({accessToken:'old',refreshToken:'refresh'});
  await assert.rejects(client.call('GET','/api/v1/me'),{code:'INVALID_AUTH_RESPONSE'});
  assert.equal(client.tokens,null);
});
test('malformed JSON response does not become successful empty data',async()=>{
  const client=new ApiClient(async()=>({ok:true,status:200,text:async()=>'<html>Error</html>'}));
  await assert.rejects(client.call('GET','/api/v1/status'),{code:'INVALID_SERVER_RESPONSE'});
});
