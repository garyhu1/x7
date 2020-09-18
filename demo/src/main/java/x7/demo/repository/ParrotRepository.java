package x7.demo.repository;

import io.xream.sqli.repository.api.BaseRepository;
import io.xream.sqli.repository.api.ResultMapRepository;
import org.springframework.stereotype.Repository;
import x7.demo.entity.Parrot;

@Repository
public interface ParrotRepository extends BaseRepository<Parrot>, ResultMapRepository {

}
