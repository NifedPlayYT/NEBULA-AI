import {createClient} from "npm:@supabase/supabase-js@2";
import {complete,type Message} from "../_shared/llm.ts";
Deno.serve(async(req)=>{
 if(req.method!=="POST")return new Response("Method not allowed",{status:405});
 const url=Deno.env.get("SUPABASE_URL"),key=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");if(!url||!key)return new Response("Configuration error",{status:500});
 try{const {branch_id,user_id}=await req.json();if(typeof branch_id!=="string"||typeof user_id!=="string")return new Response("Invalid input",{status:400});
 const db=createClient(url,key);const {data:branch}=await db.from("chat_branches").select("id").eq("id",branch_id).eq("user_id",user_id).maybeSingle();if(!branch)return new Response("Not found",{status:404});
 const [{data:rows},{data:old}]=await Promise.all([db.from("messages").select("role,content").eq("branch_id",branch_id).order("created_at",{ascending:true}).limit(150),db.from("conversation_summaries").select("summary,summarized_message_count").eq("branch_id",branch_id).maybeSingle()]);
 const list=rows??[],prev=old?.summarized_message_count??0;if(list.length-prev<15)return Response.json({skipped:true});
 const transcript=list.slice(Math.max(0,prev-5)).map((m:any)=>m.role+": "+m.content).join("\n").slice(-16000);
 const prompt:Message[]=[{role:"system",content:"Составь точное краткое резюме ролевого диалога на русском. Сохраняй факты, события, места, инвентарь, обещания и статус отношений. Не выдумывай. До 1600 символов."},{role:"user",content:"Старое резюме: "+(old?.summary??"нет")+"\nНовые сообщения:\n"+transcript}];
 const summary=await complete(prompt,450);const {error}=await db.from("conversation_summaries").upsert({branch_id,user_id,summary,summarized_message_count:list.length,updated_at:new Date().toISOString()},{onConflict:"branch_id"});
 if(error)return new Response("Save failed",{status:500});return Response.json({summarized:list.length});
 }catch{return new Response("Summary failed",{status:500});}
});
