package org.tiglor.content.service.impl;

import org.tiglor.content.entity.Category;
import org.tiglor.content.mapper.CategoryMapper;
import org.tiglor.content.service.CategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
}
