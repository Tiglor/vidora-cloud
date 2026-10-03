package com.video.platform.interactservice.service.impl;

import com.video.platform.interactservice.entity.Comment;
import com.video.platform.interactservice.mapper.CommentMapper;
import com.video.platform.interactservice.service.CommentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {
}
