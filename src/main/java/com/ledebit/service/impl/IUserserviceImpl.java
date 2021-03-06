package com.ledebit.service.impl;

import com.ledebit.common.Const;
import com.ledebit.common.ServiceResponse;
import com.ledebit.common.TokenCache;
import com.ledebit.dao.UserMapper;
import com.ledebit.pojo.User;
import com.ledebit.service.IUserservice;
import com.ledebit.util.MD5Util;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Created by Administrator on 2017/11/30.
 */
@Service("iUserService")
public class IUserserviceImpl implements IUserservice {
    @Autowired
    private UserMapper userMapper;

    /**
     * 用户登录
     * @param name
     * @param password
     * @return
     */
    public ServiceResponse<User> login(String username,String password){
        //校验是否存在
        int resultcount=userMapper.checkname(username);
        if(resultcount==0)
            return ServiceResponse.createbyerror("用户不存在");
        String MD5password= MD5Util.MD5EncodeUtf8(password);
        User user=userMapper.login(username,MD5password);
        if(user==null){
            return ServiceResponse.createbyerror("密码不正确");
        }
        user.setPassword(StringUtils.EMPTY);
        return ServiceResponse.createbysuccess("登录成功",user);
    }

    /**
     * 注册新用户
     * @param user
     * @return
     */
    public ServiceResponse<String> register(User user){
        ServiceResponse validResponse=this.Checkvalid(user.getUsername(),Const.USERNAME);
        if(!validResponse.issuccess()){
            return validResponse;
        }
        validResponse=this.Checkvalid(user.getEmail(),Const.EMAIL);
        if(!validResponse.issuccess()){
            return validResponse;
        }
        //将注册的user里面的角色设置为顾客，并加密码字段用md5加密技术进行加密
        user.setRole(Const.Role.ROLECOSTOMMER);
        user.setPassword(MD5Util.MD5EncodeUtf8(user.getPassword()));
        //输入到数据库中
        int resultcount=userMapper.insert(user);
        if(resultcount==0){
            return ServiceResponse.createbyerror("未知异常，注册失败");
        }
        return ServiceResponse.createbysuccessmsg("注册成功");
    }


    /**
     * 接受当前用户的信息
     * @param userid
     * @return
     */
    public ServiceResponse<User> getinfo(Integer userid) {
        User user =userMapper.selectByPrimaryKey(userid);
        if(user==null){
            return ServiceResponse.createbyerror("找不到该用户");
        }
        user.setPassword(StringUtils.EMPTY);
        return ServiceResponse.createbysuccess(user);
    }

    /**
     * 校验用户是否为管理员,用于后续校验管理员操作用
     * @param user
     * @return
     */
    public ServiceResponse checkadminrole(User user){
        if(user != null && user.getRole().intValue()== Const.Role.ROLEMANGER){
            return ServiceResponse.createbysuccess();
        }
        return ServiceResponse.createbyerror();
    }

    /**
     * 校验参数
     * @param str
     * @param type
     * @return
     */
    public ServiceResponse<String> Checkvalid(String str,String type){
        if(StringUtils.isNotBlank(type)){
            //开始校验
            if(Const.USERNAME.equals(type)){int resultcount =userMapper.checkname(str);
                if(resultcount>0){
                    return  ServiceResponse.createbyerror("用户已存在");
                }
            }
            if(Const.EMAIL.equals(type)){
                int resultcount =userMapper.checkemail(str);
                if(resultcount>0){
                    return  ServiceResponse.createbyerror("邮箱已存在");
                }
            }
        }else {
            return ServiceResponse.createbyerror("参数错误");
        }
        return ServiceResponse.createbysuccessmsg("校验成功");
    }

    /**
     * 获取用户的找回密码问题
     * @param username
     * @return
     */
    public ServiceResponse<String> Forgetquestion(String username) {
        ServiceResponse validresponse =this.Checkvalid(username,Const.USERNAME);
        if(validresponse.issuccess()){
            return ServiceResponse.createbyerror("用户不存在");
        }
        String Question=userMapper.userquestion(username);
        if(StringUtils.isNotBlank(Question)){
            return ServiceResponse.createbysuccess(Question);
        }
        return  ServiceResponse.createbyerror("找回密码的问题是空的");
    }

    /**
     * 校验找回密码答案
     * @param username
     * @param question
     * @param answer
     * @return
     */
    public ServiceResponse<String> Checkanswer(String username, String question, String answer) {
        int resultcount =userMapper.checkanswer(username,question,answer);
        if(resultcount>0){
            String fortaken= UUID.randomUUID().toString();
            TokenCache.setkey(TokenCache.TokenProfix+username,fortaken);
            return ServiceResponse.createbysuccess(fortaken);
        }
        return ServiceResponse.createbyerror("问题与答案有误！");
    }

    /**
     * 重置密码
     * @param username
     * @param passwordNew
     * @param forgetToken
     * @return
     */
    public ServiceResponse<String> Resetpassword(String username, String passwordNew, String forgetToken) {
        //校验参数
        if(StringUtils.isNotBlank(forgetToken)){
            return ServiceResponse.createbyerror("参数错误，token需要传递");
        }
        //校验用户名，变量控制防止越权
        ServiceResponse validresponse =this.Checkvalid(username,Const.USERNAME);
        if(validresponse.issuccess()){
            return ServiceResponse.createbyerror("用户不存在");
        }
        //参看缓存里的username和参数，校验是否为空
        String token=TokenCache.getKey(TokenCache.TokenProfix+username);
        if(StringUtils.isNotBlank(token)){
            return ServiceResponse.createbyerror("token已过期或无效");
        }
        if(StringUtils.equals(forgetToken,token)){
            String md5password=MD5Util.MD5EncodeUtf8(passwordNew);
            int rowcount=userMapper.resetpassword(username,md5password);
            if(rowcount>0){
                return ServiceResponse.createbysuccessmsg("修改密码成功");
            }else{
                return ServiceResponse.createbyerror("token错修改失败");
            }
        }
        return ServiceResponse.createbyerror("修改密码失败");
    }

    public ServiceResponse<String> ResetPasswordOL(User user, String passwordnew, String passwordold) {
        int resultcount=userMapper.checkpassword(MD5Util.MD5EncodeUtf8(passwordold),user.getId());
        if(resultcount==0){
            return ServiceResponse.createbyerror("旧密码错误");
        }
        user.setPassword(MD5Util.MD5EncodeUtf8(passwordnew));
        int updatecount=userMapper.updateByPrimaryKeySelective(user);
        if(updatecount>0){
            return  ServiceResponse.createbysuccessmsg("密码修改成功");
        }
        return ServiceResponse.createbyerror("密码修改失败");
    }

    public ServiceResponse<User> updateinfo(User user){
        //更新时不能更新用户名username
        //校验新邮箱，存在相同但不能是当前用户的，返回更新错误
        int resultcount=userMapper.checkemailbyuserid(user.getId(),user.getEmail());
        if(resultcount>0){
            return ServiceResponse.createbyerror("邮箱已存在，请重新填写信息");
        }
        User updateuser=new User();
        updateuser.setId(user.getId());
        updateuser.setEmail(user.getEmail());
        updateuser.setQuestion(user.getQuestion());
        updateuser.setAnswer(user.getAnswer());
        updateuser.setPhone(user.getPhone());

        int updatecount=userMapper.updateByPrimaryKeySelective(updateuser);
        if(updatecount>0){
            return  ServiceResponse.createbysuccessmsg("更新个人对象成功");
        }
        return ServiceResponse.createbyerror("更新个人信息失败");

    }

}
