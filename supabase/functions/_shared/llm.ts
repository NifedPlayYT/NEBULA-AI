export type Message={role:"system"|"user"|"assistant";content:string};
export class ProviderError extends Error{constructor(message:string,readonly status:number,readonly code:string){super(message);}}
async function post(url:string,key:string,body:unknown):Promise<any>{
 const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),45000);
 try{const response=await fetch(url,{method:"POST",headers:{"content-type":"application/json",authorization:`Bearer ${key}`},body:JSON.stringify(body),signal:controller.signal});
 const data=await response.json().catch(()=>({}));if(!response.ok){const raw=JSON.stringify(data);
 if(response.status===429)throw new ProviderError("Превышен лимит запросов.",429,"rate_limit");
 if(response.status===400&&/content.*risk|risk.*content/i.test(raw))throw new ProviderError("Запрос отклонён фильтром безопасности.",400,"content_risk");
 throw new ProviderError("Ошибка провайдера модели.",response.status,"provider_error");}return data;
 }catch(e){if(e instanceof ProviderError)throw e;if(e instanceof DOMException&&e.name==="AbortError")throw new ProviderError("Истекло время ожидания модели.",504,"timeout");throw new ProviderError("Сетевая ошибка модели.",502,"network");}finally{clearTimeout(timeout);}
}
export async function complete(messages:Message[],maxTokens:number):Promise<string>{
 const useRouter=Deno.env.get("LLM_PROVIDER")==="openrouter";
 const key=Deno.env.get(useRouter?"OPENROUTER_API_KEY":"DEEPSEEK_API_KEY");
 if(!key)throw new ProviderError("Ключ провайдера не настроен на сервере.",500,"configuration");
 const endpoint=useRouter?"https://openrouter.ai/api/v1/chat/completions":"https://api.deepseek.com/chat/completions";
 const model=useRouter?(Deno.env.get("OPENROUTER_MODEL")??"deepseek/deepseek-chat"):(Deno.env.get("DEEPSEEK_MODEL")??"deepseek-chat");
 const result=await post(endpoint,key,{model,messages,max_tokens:maxTokens,temperature:.82});
 const answer=result?.choices?.[0]?.message?.content;
 if(typeof answer!=="string"||!answer.trim())throw new ProviderError("Модель вернула пустой ответ.",502,"empty_response");
 return answer.trim();
}
function normalizeVector(values: number[]): number[] {
 const norm=Math.sqrt(values.reduce((sum,value)=>sum+value*value,0));
 return norm>0?values.map(value=>value/norm):values;
}

function poolEmbedding(payload: unknown): number[] | null {
 const isVector=(value: unknown): value is number[] =>
  Array.isArray(value)&&value.length===1024&&value.every((item)=>typeof item==="number"&&Number.isFinite(item));
 if(isVector(payload)) return normalizeVector(payload);

 let matrix: unknown=payload;
 if(Array.isArray(matrix)&&matrix.length===1&&Array.isArray(matrix[0])&&Array.isArray(matrix[0][0])) {
  matrix=matrix[0];
 }
 if(!Array.isArray(matrix)||matrix.length===0||!matrix.every((row)=>Array.isArray(row)&&row.length===1024&&row.every((item)=>typeof item==="number"&&Number.isFinite(item)))) {
  return null;
 }
 const rows=matrix as number[][];
 if(rows.length===1) return normalizeVector(rows[0]);
 const pooled=Array.from({length:1024},(_,index)=>rows.reduce((sum,row)=>sum+row[index],0)/rows.length);
 return normalizeVector(pooled);
}

export async function embed(text:string):Promise<number[]|null>{
 const key=Deno.env.get("HUGGINGFACE_API_KEY");if(!key)return null;
 const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),20000);
 try{
  const response=await fetch("https://router.huggingface.co/hf-inference/models/BAAI/bge-m3/pipeline/feature-extraction",{
   method:"POST",
   headers:{authorization:`Bearer ${key}`,"content-type":"application/json"},
   body:JSON.stringify({inputs:text,options:{wait_for_model:true}}),
   signal:controller.signal
  });
  if(!response.ok)return null;
  const payload=await response.json();
  return poolEmbedding(payload?.embedding??payload);
 }catch{return null;}finally{clearTimeout(timeout);}
}
