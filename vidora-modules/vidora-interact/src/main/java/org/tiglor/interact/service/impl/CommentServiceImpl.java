package org.tiglor.interact.service.impl;

import org.tiglor.interact.entity.Comment;
import org.tiglor.interact.mapper.CommentMapper;
import org.tiglor.interact.service.CommentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {
}
