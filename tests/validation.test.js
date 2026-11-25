import test from 'node:test';
import assert from 'node:assert/strict';
import {validateFields} from '../src/web/validation.js';
const specs=[{name:'username',label:'Tên đăng nhập',required:true,maxLength:128}, {name:'pin',label:'PIN',pattern:'[0-9]{6,12}',type:'password',hint:'PIN cần từ 6 đến 12 chữ số.'}];
test('required, blank and bounded fields are validated',()=>{
  for(const username of ['', '   ', 'a'.repeat(129)])assert.ok(validateFields(specs,{username,pin:'123456'}).username);
  assert.deepEqual(validateFields(specs,{username:'student01',pin:'123456'}),{});
});
test('PIN rejects non-digits and lengths outside 6–12',()=>{
  for(const pin of ['', '12345','1'.repeat(13),'abcdef','123 56','１２３４５６'])assert.ok(validateFields(specs,{username:'user',pin}).pin);
  for(const pin of ['123456','123456789012'])assert.deepEqual(validateFields(specs,{username:'user',pin}),{});
});
test('email and optional fields have consistent behavior',()=>{
  const fields=[{name:'email',label:'Email',type:'email',required:false}];
  assert.deepEqual(validateFields(fields,{}),{});
  for(const email of ['abc','a@','@example.test','a @example.test'])assert.ok(validateFields(fields,{email}).email);
  assert.deepEqual(validateFields(fields,{email:'student@example.test'}),{});
});
test('selection is restricted to listed accounts',()=>{
  const fields=[{name:'account',label:'Tài khoản',options:[{value:'id-1'}]}];
  assert.ok(validateFields(fields,{account:'invented'}).account);
  assert.deepEqual(validateFields(fields,{account:'id-1'}),{});
});
test('PIN confirmation and minimum lengths are validated',()=>{
  assert.ok(validateFields([],{newPin:'123456',confirmPin:'654321'}).confirmPin);
  assert.ok(validateFields([{name:'reason',label:'Lý do',minLength:5}],{reason:'abc'}).reason);
});
