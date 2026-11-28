import {createClient} from "npm:@supabase/supabase-js@2";
Deno.serve(async(req)=>{
 if(req.method!=="POST")return new Response("Method not allowed",{status:405});
 const url=Deno.env.get("SUPABASE_URL"),anon=Deno.env.get("SUPABASE_ANON_KEY"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY"),auth=req.headers.get("Authorization");
 if(!url||!anon||!service||!auth)return new Response("Unauthorized",{status:401});
 const userClient=createClient(url,anon,{global:{headers:{Authorization:auth}}});const {data,error}=await userClient.auth.getUser();
 if(error||!data.user)return new Response("Unauthorized",{status:401});
 const admin=createClient(url,service);const result=await admin.auth.admin.deleteUser(data.user.id);
 if(result.error)return new Response("Deletion failed",{status:500});return Response.json({deleted:true});
});
