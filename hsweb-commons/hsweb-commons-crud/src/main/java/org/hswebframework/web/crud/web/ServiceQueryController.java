package org.hswebframework.web.crud.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.hswebframework.ezorm.rdb.mapping.ReactiveRepository;
import org.hswebframework.web.api.crud.entity.PagerResult;
import org.hswebframework.web.api.crud.entity.QueryNoPagingOperation;
import org.hswebframework.web.api.crud.entity.QueryOperation;
import org.hswebframework.web.api.crud.entity.QueryParamEntity;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.annotation.QueryAction;
import org.hswebframework.web.crud.service.CrudService;
import org.hswebframework.web.exception.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;

/**
 * 基于{@link CrudService}的查询控制器.
 *
 * @param <E> 实体类
 * @param <K> 主键类型
 * @see CrudService
 */
public interface ServiceQueryController<E, K> {

    @Authorize(ignore = true)
    CrudService<E, K> getService();

    /**
     * 查询,但是不返回分页结果.
     *
     * <pre>
     *     GET /_query/no-paging?pageIndex=0&pageSize=20&where=name is 张三&orderBy=id desc
     * </pre>
     *
     * @param query 动态查询条件
     * @return 结果流
     * @see QueryParamEntity
     */
    @GetMapping("/_query/no-paging")
    @QueryAction
    @QueryOperation(summary = "Dynamically querying using GET pagination (does not return totals)",
            description = "此操作不返回分页总数,如果需要获取全部数据,请设置参数paging=false")
    default List<E> query(@Parameter(hidden = true) QueryParamEntity query) {
        return getService()
                .createQuery()
                .setParam(query)
                .fetch();
    }

    /**
     * POST方式查询.不返回分页结果
     *
     * <pre>
     *     POST /_query/no-paging
     *
     *     {
     *         "pageIndex":0,
     *         "pageSize":20,
     *         "where":"name like 张%", //放心使用,没有SQL注入
     *         "orderBy":"id desc",
     *         "terms":[ //高级条件
     *             {
     *                 "column":"name",
     *                 "termType":"like",
     *                 "value":"张%"
     *             }
     *         ]
     *     }
     * </pre>
     *
     * @param query 查询条件
     * @return 结果流
     * @see QueryParamEntity
     */
    @PostMapping("/_query/no-paging")
    @QueryAction
    @Operation(summary = "Paged dynamic query using POST (does not return total)",
            description = "此操作不返回分页总数,如果需要获取全部数据,请设置参数paging=false")
    default List<E> postQuery(@RequestBody QueryParamEntity query) {
        return this.query(query);
    }


    /**
     * GET方式分页查询
     *
     * <pre>
     *    GET /_query/no-paging?pageIndex=0&pageSize=20&where=name is 张三&orderBy=id desc
     * </pre>
     *
     * @param query 查询条件
     * @return 分页查询结果
     * @see PagerResult
     */
    @GetMapping("/_query")
    @QueryAction
    @QueryOperation(summary = "Use get to page dynamic queries")
    default PagerResult<E> queryPager(@Parameter(hidden = true) QueryParamEntity query) {
        if (query.getTotal() != null) {
            return PagerResult
                    .of(query.getTotal(),
                        getService()
                                .createQuery()
                                .setParam(query.rePaging(query.getTotal()))
                                .fetch(), query)
                    ;
        }
        int total = getService().createQuery().setParam(query.clone()).count();
        if (total == 0) {
            return PagerResult.of(0, Collections.emptyList(), query);
        }
        return PagerResult
                .of(total,
                    getService()
                            .createQuery()
                            .setParam(query.rePaging(total))
                            .fetch(), query);
    }


    @PostMapping("/_query")
    @QueryAction
    @SuppressWarnings("all")
    @Operation(summary = "Use THE POST method to page dynamic queries")
    default PagerResult<E> postQueryPager(@RequestBody QueryParamEntity query) {
        return queryPager(query);
    }

    @PostMapping("/_count")
    @QueryAction
    @Operation(summary = "Use the POST method to query the total number")
    default int postCount(@RequestBody QueryParamEntity query) {
         return this.count(query);
    }

    /**
     * 统计查询
     *
     * <pre>
     *     GET /_count
     * </pre>
     *
     * @param query 查询条件
     * @return 统计结果
     */
    @GetMapping("/_count")
    @QueryAction
    @QueryNoPagingOperation(summary = "Use the GET method to query the total number")
    default int count(@Parameter(hidden = true) QueryParamEntity query) {
        return getService()
                .createQuery()
                .setParam(query)
                .count();
    }

    @GetMapping("/{id:.+}")
    @QueryAction
    @Operation(summary = "Query based on ID")
    default E getById(@PathVariable K id) {
       return getService()
                .findById(id)
               .orElseThrow(NotFoundException::new);
    }

}
